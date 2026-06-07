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
import com.sanjeevsky.orderservice.model.SagaInstance;
import com.sanjeevsky.orderservice.model.ShippingAddress;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.repository.SagaInstanceRepository;
import com.sanjeevsky.orderservice.service.OrderSagaOrchestrator;
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
    private final SagaInstanceRepository sagaRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;

    @Override
    public Order getOrderById(String userId, UUID id) {
        String normalizedUserId = validateUserId(userId);
        validateOrderId(id);
        log.info("Fetching order id={} for user={}", id, normalizedUserId);
        return orderRepository.findByIdAndUserId(id, normalizedUserId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }

    @Override
    public Order createOrder(String userId, UUID addressId, String couponCode) {
        return createOrder(userId, addressId, couponCode, null);
    }

    @Override
    public Order createOrder(String userId, UUID addressId, String couponCode, String idempotencyKey) {
        String normalizedUserId = validateUserId(userId);
        validateCreateOrderRequest(addressId);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedCouponCode = normalizeCouponCode(couponCode);
        log.info("Creating order for user={}, addressId={}, couponCode={}, idempotencyKey={}",
                normalizedUserId, addressId, normalizedCouponCode, normalizedIdempotencyKey);

        if (normalizedIdempotencyKey != null) {
            return orderRepository.findByUserIdAndIdempotencyKey(normalizedUserId, normalizedIdempotencyKey)
                    .map(existing -> {
                        validateIdempotentReplay(existing, addressId, normalizedCouponCode, normalizedIdempotencyKey);
                        if (existing.getPaymentId() == null) {
                            log.info("Completing existing order id={} for user={}, idempotencyKey={}",
                                    existing.getId(), normalizedUserId, normalizedIdempotencyKey);
                            return completeOrderCheckout(existing, normalizedUserId, addressId, normalizedIdempotencyKey);
                        }
                        log.info("Returning existing order id={} for user={}, idempotencyKey={}",
                                existing.getId(), normalizedUserId, normalizedIdempotencyKey);
                        return existing;
                    })
                    .orElseGet(() -> createNewOrder(normalizedUserId, addressId, normalizedCouponCode, normalizedIdempotencyKey));
        }

        return createNewOrder(normalizedUserId, addressId, normalizedCouponCode, null);
    }

    @Override
    public Order createOrderSaga(String userId, UUID addressId, String couponCode, String idempotencyKey, boolean simulatePaymentFailure) {
        String normalizedUserId = validateUserId(userId);
        validateCreateOrderRequest(addressId);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedCouponCode = normalizeCouponCode(couponCode);
        log.info("Starting saga checkout for user={}, addressId={}, couponCode={}, idempotencyKey={}, simulatePaymentFailure={}",
                normalizedUserId, addressId, normalizedCouponCode, normalizedIdempotencyKey, simulatePaymentFailure);

        if (normalizedIdempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(normalizedUserId, normalizedIdempotencyKey);
            if (existing.isPresent()) {
                Order order = existing.get();
                validateIdempotentReplay(order, addressId, normalizedCouponCode, normalizedIdempotencyKey);
                log.info("Returning existing saga order id={} for user={}, idempotencyKey={}",
                        order.getId(), normalizedUserId, normalizedIdempotencyKey);
                return order;
            }
        }

        Order savedOrder = buildAndSavePendingOrder(normalizedUserId, addressId, normalizedCouponCode, normalizedIdempotencyKey);
        sagaOrchestrator.startSaga(savedOrder, simulatePaymentFailure);
        return savedOrder;
    }

    @Override
    public SagaInstance getSaga(String userId, UUID orderId) {
        String normalizedUserId = validateUserId(userId);
        validateOrderId(orderId);
        // Ensures the order belongs to the caller before exposing its saga state.
        orderRepository.findByIdAndUserId(orderId, normalizedUserId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No saga found for order: " + orderId));
    }

    private void validateCreateOrderRequest(UUID addressId) {
        if (addressId == null) {
            throw new InvalidRequestException("Order addressId is required");
        }
    }

    private Order createNewOrder(String userId, UUID addressId, String couponCode, String idempotencyKey) {
        Order savedOrder = buildAndSavePendingOrder(userId, addressId, couponCode, idempotencyKey);
        return completeOrderCheckout(savedOrder, userId, addressId, idempotencyKey);
    }

    /**
     * Builds a {@code PENDING} order from the cart/address/coupon and persists it. Shared by the
     * legacy synchronous checkout and the saga checkout — neither charges payment here.
     */
    private Order buildAndSavePendingOrder(String userId, UUID addressId, String couponCode, String idempotencyKey) {

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

        if (couponCode != null && !couponCode.isBlank()) {
            CouponValidationResult validation = couponFeignClient.validateCoupon(couponCode, cartTotal);
            if (validation.isValid()) {
                discount = validation.getDiscountAmount();
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
                .couponCode(couponCode)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved id={}", savedOrder.getId());

        return savedOrder;
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
        String normalizedUserId = validateUserId(userId);
        validateOrderId(orderId);
        log.info("Confirming order id={} for user={}", orderId, normalizedUserId);
        Order order = orderRepository.findByIdAndUserId(orderId, normalizedUserId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order already confirmed id={} for user={}", orderId, normalizedUserId);
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidRequestException("Order is not in PENDING state");
        }
        validatePaymentId(order.getPaymentId());
        paymentFeignClient.confirmPayment(order.getPaymentId());
        order.setStatus(OrderStatus.CONFIRMED);
        Order confirmed = orderRepository.save(order);

        eventPublisher.publishOrderConfirmed(OrderConfirmedEvent.builder()
                .orderId(confirmed.getId())
                .userId(normalizedUserId)
                .totalAmount(confirmed.getOrderTotal())
                .items(orderItemEvents(confirmed))
                .build());

        return confirmed;
    }

    @Override
    public Order cancelOrder(String userId, UUID orderId) {
        String normalizedUserId = validateUserId(userId);
        validateOrderId(orderId);
        log.info("Cancelling order id={} for user={}", orderId, normalizedUserId);
        Order order = orderRepository.findByIdAndUserId(orderId, normalizedUserId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order already cancelled id={} for user={}", orderId, normalizedUserId);
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
                .userId(normalizedUserId)
                .reason("Cancelled by user")
                .build());

        return cancelled;
    }

    @Override
    public List<Order> getOrdersByUser(String userId) {
        String normalizedUserId = validateUserId(userId);
        log.info("Fetching all orders for user={}", normalizedUserId);
        return orderRepository.findAllByUserId(normalizedUserId);
    }

    private String validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidRequestException("Order userId is required");
        }
        return userId.trim();
    }

    private void validateOrderId(UUID orderId) {
        if (orderId == null) {
            throw new InvalidRequestException("Order id is required");
        }
    }

    private void validatePaymentId(UUID paymentId) {
        if (paymentId == null) {
            throw new InvalidRequestException("Order paymentId is required");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCouponCode(String couponCode) {
        if (couponCode == null) {
            return null;
        }
        String trimmed = couponCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateIdempotentReplay(Order existing, UUID addressId, String couponCode, String idempotencyKey) {
        UUID existingAddressId = existing.getShippingAddress() == null
                ? null
                : existing.getShippingAddress().getOriginalAddressId();
        if (!Objects.equals(existingAddressId, addressId)
                || !Objects.equals(normalizeCouponCode(existing.getCouponCode()), couponCode)) {
            throw new InvalidRequestException(
                    "Idempotency key " + idempotencyKey + " was already used for a different order request");
        }
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
