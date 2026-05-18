package com.sanjeevsky.customerservice.service.impl;

import com.sanjeevsky.customerservice.clients.CartFeignClient;
import com.sanjeevsky.customerservice.clients.PaymentFeignClient;
import com.sanjeevsky.customerservice.events.OrderEventPublisher;
import com.sanjeevsky.customerservice.exceptions.AddressDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.InvalidRequestException;
import com.sanjeevsky.customerservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.model.OrderItem;
import com.sanjeevsky.customerservice.repository.AddressRepository;
import com.sanjeevsky.customerservice.repository.OrderRepository;
import com.sanjeevsky.customerservice.service.OrderService;
import com.sanjeevsky.platform.events.OrderCancelledEvent;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.events.OrderItemEvent;
import com.sanjeevsky.platform.events.OrderPlacedEvent;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sanjeevsky.customerservice.utils.ErrorConstants.ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CartFeignClient cartFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final OrderEventPublisher eventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository,
                            AddressRepository addressRepository,
                            CartFeignClient cartFeignClient,
                            PaymentFeignClient paymentFeignClient,
                            OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.cartFeignClient = cartFeignClient;
        this.paymentFeignClient = paymentFeignClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Order getOrderById(String user, UUID id) {
        log.info("Fetching order with id={} for user={}", id, user);
        return orderRepository.findByIdAndUserId(id, user)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }

    @Override
    public Order createOrder(String userId, UUID addressId) {
        log.info("Creating order for user={} with addressId={}", userId, addressId);

        CartSnapshot cart = cartFeignClient.getCheckoutSnapshot(userId);
        log.info("Fetched cart snapshot for user={}, itemCount={}", userId,
                cart.getItems() != null ? cart.getItems().size() : 0);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidRequestException("Cart is empty");
        }

        Address address = addressRepository.findByIdAndUser(addressId, userId)
                .orElseThrow(() -> new AddressDoesnotExistsException(ADDRESS_WITH_GIVEN_UUID_DOES_NOT_EXIST));
        log.info("Resolved address id={} for user={}", addressId, userId);

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
                .address(address)
                .orderItems(orderItems)
                .orderTotal(cart.getTotalAmount())
                .status(OrderStatus.PENDING)
                .discount(0)
                .shippingCharges(0)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        log.info("Saving order for user={}", userId);

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with id={}", savedOrder.getId());

        PaymentResponse payment = paymentFeignClient.initiatePayment(
                new PaymentRequest(savedOrder.getId(), userId, savedOrder.getOrderTotal()));
        log.info("Payment initiated with id={} for order={}", payment.getId(), savedOrder.getId());

        savedOrder.setPaymentId(payment.getId());
        Order finalOrder = orderRepository.save(savedOrder);
        log.info("Order updated with paymentId={}", payment.getId());

        cartFeignClient.clearCart(userId);
        log.info("Cart cleared for user={}", userId);

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
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
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
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
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
