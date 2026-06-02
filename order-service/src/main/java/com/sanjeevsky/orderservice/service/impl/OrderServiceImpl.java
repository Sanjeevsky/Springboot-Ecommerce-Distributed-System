package com.sanjeevsky.orderservice.service.impl;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.clients.CouponFeignClient;
import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.clients.InventoryFeignClient;
import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.events.OrderEventPublisher;
import com.sanjeevsky.orderservice.exceptions.AddressNotFoundException;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.orderservice.model.AddressDto;
import com.sanjeevsky.orderservice.model.CouponValidationResult;
import com.sanjeevsky.orderservice.model.InventoryStock;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.ShippingAddress;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.OrderService;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.OrderItemEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import com.sanjeevsky.platform.model.cart.CartItemSnapshot;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartFeignClient cartFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final CustomerFeignClient customerFeignClient;
    private final CouponFeignClient couponFeignClient;
    private final InventoryFeignClient inventoryFeignClient;
    private final OrderEventPublisher eventPublisher;

    @Override
    public Order getOrderById(String userId, UUID id) {
        log.info("Fetching order id={} for user={}", id, userId);
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }

    @Override
    public Order createOrder(String userId, UUID addressId, String couponCode) {
        return createOrder(userId, addressId, couponCode, null);
    }

    @Override
    public Order createOrder(String userId, UUID addressId, String couponCode, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        log.info("Creating order for user={}, addressId={}, couponCode={}, idempotencyKey={}",
                userId, addressId, couponCode, normalizedIdempotencyKey);

        if (normalizedIdempotencyKey != null) {
            return orderRepository.findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey)
                    .map(existing -> {
                        if (existing.getPaymentId() == null) {
                            log.info("Completing existing order id={} for user={}, idempotencyKey={}",
                                    existing.getId(), userId, normalizedIdempotencyKey);
                            return completeOrderCheckout(existing, userId, addressId, normalizedIdempotencyKey);
                        }
                        log.info("Returning existing order id={} for user={}, idempotencyKey={}",
                                existing.getId(), userId, normalizedIdempotencyKey);
                        return existing;
                    })
                    .orElseGet(() -> createNewOrder(userId, addressId, couponCode, normalizedIdempotencyKey));
        }

        return createNewOrder(userId, addressId, couponCode, null);
    }

    private Order createNewOrder(String userId, UUID addressId, String couponCode, String idempotencyKey) {

        CartSnapshot cart = cartFeignClient.getCheckoutSnapshot(userId);
        if (cart == null) {
            throw new InvalidRequestException("Cart unavailable");
        }
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidRequestException("Cart is empty");
        }

        AddressDto address = customerFeignClient.getAddress(userId, addressId);
        if (address == null) {
            throw new AddressNotFoundException("Address not found: " + addressId);
        }

        ShippingAddress shippingAddress = ShippingAddress.builder()
                .originalAddressId(addressId)
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .zipCode(address.getZipCode())
                .home(address.getHome())
                .streetLocality(address.getStreetLocality())
                .landmark(address.getLandmark())
                .build();

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(item -> OrderItem.builder()
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .qty(item.getQty())
                        .build())
                .collect(Collectors.toList());

        validateInventoryAvailability(cart);

        double cartTotal = cart.getTotalAmount();
        double discount = 0;
        String appliedCoupon = null;

        if (couponCode != null && !couponCode.isBlank()) {
            CouponValidationResult validation = couponFeignClient.validateCoupon(couponCode, cartTotal);
            if (validation.isValid()) {
                discount = validation.getDiscountAmount();
                appliedCoupon = couponCode;
                log.info("Coupon {} applied: discount={}", couponCode, discount);
                couponFeignClient.applyCoupon(couponCode);
            } else {
                log.warn("Coupon {} invalid: {}", couponCode, validation.getMessage());
            }
        }

        double orderTotal = Math.max(0, cartTotal - discount);

        Order order = Order.builder()
                .userId(userId)
                .shippingAddress(shippingAddress)
                .orderItems(orderItems)
                .orderTotal(orderTotal)
                .status(OrderStatus.PENDING)
                .discount(discount)
                .shippingCharges(0)
                .idempotencyKey(idempotencyKey)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved id={}", savedOrder.getId());

        return completeOrderCheckout(savedOrder, userId, addressId, idempotencyKey);
    }

    private Order completeOrderCheckout(Order savedOrder, String userId, UUID addressId, String idempotencyKey) {
        PaymentResponse payment = paymentFeignClient.initiatePayment(
                new PaymentRequest(savedOrder.getId(), userId, savedOrder.getOrderTotal(), paymentIdempotencyKey(idempotencyKey)));
        if (payment == null) {
            throw new InvalidRequestException("Payment initiation failed");
        }
        log.info("Payment initiated id={} for order={}", payment.getId(), savedOrder.getId());

        savedOrder.setPaymentId(payment.getId());
        Order finalOrder = orderRepository.save(savedOrder);

        cartFeignClient.clearCart(userId);

        eventPublisher.publishOrderPlaced(OrderPlacedEvent.builder()
                .orderId(finalOrder.getId())
                .userId(userId)
                .totalAmount(finalOrder.getOrderTotal())
                .addressId(orderAddressId(finalOrder, addressId))
                .items(orderItemEvents(finalOrder))
                .build());

        return finalOrder;
    }

    @Override
    public Order confirmOrder(String userId, UUID orderId) {
        log.info("Confirming order id={} for user={}", orderId, userId);
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order already confirmed id={} for user={}", orderId, userId);
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidRequestException("Order is not in PENDING state");
        }
        paymentFeignClient.confirmPayment(order.getPaymentId());
        order.setStatus(OrderStatus.CONFIRMED);
        Order confirmed = orderRepository.save(order);

        eventPublisher.publishOrderConfirmed(OrderConfirmedEvent.builder()
                .orderId(confirmed.getId())
                .userId(userId)
                .totalAmount(confirmed.getOrderTotal())
                .items(orderItemEvents(confirmed))
                .build());

        return confirmed;
    }

    @Override
    public Order cancelOrder(String userId, UUID orderId) {
        log.info("Cancelling order id={} for user={}", orderId, userId);
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order already cancelled id={} for user={}", orderId, userId);
            return order;
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidRequestException("Order cannot be cancelled in state: " + order.getStatus());
        }
        if (order.getPaymentId() != null) {
            paymentFeignClient.refundPayment(order.getPaymentId());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order cancelled = orderRepository.save(order);

        eventPublisher.publishOrderCancelled(OrderCancelledEvent.builder()
                .orderId(cancelled.getId())
                .userId(userId)
                .reason("Cancelled by user")
                .build());

        return cancelled;
    }

    @Override
    public List<Order> getOrdersByUser(String userId) {
        log.info("Fetching all orders for user={}", userId);
        return orderRepository.findAllByUserId(userId);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String paymentIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null ? null : "order:" + idempotencyKey;
    }

    private void validateInventoryAvailability(CartSnapshot cart) {
        for (CartItemSnapshot item : cart.getItems()) {
            List<InventoryStock> stockEntries;
            try {
                stockEntries = inventoryFeignClient.getStockByProduct(item.getProductId());
            } catch (RuntimeException ex) {
                log.warn("Inventory pre-check skipped for productId={} variantId={}: {}",
                        item.getProductId(), item.getVariantId(), ex.getMessage());
                continue;
            }

            if (stockEntries == null) {
                log.warn("Inventory pre-check skipped for productId={} variantId={} because inventory-service fallback returned no data",
                        item.getProductId(), item.getVariantId());
                continue;
            }

            Optional<InventoryStock> matchingStock = stockEntries.stream()
                    .filter(stock -> Objects.equals(stock.getVariantId(), item.getVariantId()))
                    .findFirst();

            int available = matchingStock.map(InventoryStock::availableQuantity).orElse(0);
            if (available < item.getQty()) {
                throw new InvalidRequestException("Insufficient stock for productId=" + item.getProductId()
                        + " variantId=" + item.getVariantId()
                        + ". Available=" + available + ", requested=" + item.getQty());
            }
        }
    }

    private UUID orderAddressId(Order order, UUID fallbackAddressId) {
        if (order.getShippingAddress() != null && order.getShippingAddress().getOriginalAddressId() != null) {
            return order.getShippingAddress().getOriginalAddressId();
        }
        return fallbackAddressId;
    }

    private List<OrderItemEvent> orderItemEvents(Order order) {
        return order.getOrderItems().stream()
                .map(i -> OrderItemEvent.builder()
                        .productId(i.getProductId())
                        .variantId(i.getVariantId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .qty(i.getQty())
                        .build())
                .collect(Collectors.toList());
    }
}
