package com.sanjeevsky.orderservice.service.impl;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.events.OrderEventPublisher;
import com.sanjeevsky.orderservice.exceptions.AddressNotFoundException;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.orderservice.model.AddressDto;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.ShippingAddress;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.OrderService;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.OrderItemEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final OrderEventPublisher eventPublisher;

    @Override
    public Order getOrderById(String userId, UUID id) {
        log.info("Fetching order id={} for user={}", id, userId);
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }

    @Override
    public Order createOrder(String userId, UUID addressId) {
        log.info("Creating order for user={}, addressId={}", userId, addressId);

        CartSnapshot cart = cartFeignClient.getCheckoutSnapshot(userId);
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

        Order order = Order.builder()
                .userId(userId)
                .shippingAddress(shippingAddress)
                .orderItems(orderItems)
                .orderTotal(cart.getTotalAmount())
                .status(OrderStatus.PENDING)
                .discount(0)
                .shippingCharges(0)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved id={}", savedOrder.getId());

        PaymentResponse payment = paymentFeignClient.initiatePayment(
                new PaymentRequest(savedOrder.getId(), userId, savedOrder.getOrderTotal()));
        log.info("Payment initiated id={} for order={}", payment.getId(), savedOrder.getId());

        savedOrder.setPaymentId(payment.getId());
        Order finalOrder = orderRepository.save(savedOrder);

        cartFeignClient.clearCart(userId);

        List<OrderItemEvent> itemEvents = finalOrder.getOrderItems().stream()
                .map(i -> OrderItemEvent.builder()
                        .productId(i.getProductId())
                        .variantId(i.getVariantId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .qty(i.getQty())
                        .build())
                .collect(Collectors.toList());

        eventPublisher.publishOrderPlaced(OrderPlacedEvent.builder()
                .orderId(finalOrder.getId())
                .userId(userId)
                .totalAmount(finalOrder.getOrderTotal())
                .addressId(addressId)
                .items(itemEvents)
                .build());

        return finalOrder;
    }

    @Override
    public Order confirmOrder(String userId, UUID orderId) {
        log.info("Confirming order id={} for user={}", orderId, userId);
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
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
                .build());

        return confirmed;
    }

    @Override
    public Order cancelOrder(String userId, UUID orderId) {
        log.info("Cancelling order id={} for user={}", orderId, userId);
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
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
}
