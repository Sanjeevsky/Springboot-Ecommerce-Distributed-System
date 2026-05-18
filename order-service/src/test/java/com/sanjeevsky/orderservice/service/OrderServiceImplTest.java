package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.events.OrderEventPublisher;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.orderservice.model.AddressDto;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.ShippingAddress;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.impl.OrderServiceImpl;
import com.sanjeevsky.platform.model.cart.CartItemSnapshot;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import com.sanjeevsky.platform.model.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock CartFeignClient cartFeignClient;
    @Mock PaymentFeignClient paymentFeignClient;
    @Mock CustomerFeignClient customerFeignClient;
    @Mock OrderEventPublisher eventPublisher;

    @InjectMocks OrderServiceImpl orderService;

    private static final String USER = "user@example.com";
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .id(ORDER_ID)
                .userId(USER)
                .status(OrderStatus.PENDING)
                .paymentId(PAYMENT_ID)
                .shippingAddress(ShippingAddress.builder().city("NYC").build())
                .orderTotal(100.0)
                .build();
    }

    @Test
    void getOrderById_found() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        Order result = orderService.getOrderById(USER, ORDER_ID);
        assertThat(result.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    void getOrderById_notFound_throws() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getOrderById(USER, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void createOrder_emptyCart_throws() {
        CartSnapshot emptyCart = new CartSnapshot();
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(emptyCart);
        assertThatThrownBy(() -> orderService.createOrder(USER, ADDRESS_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void createOrder_success() {
        CartItemSnapshot item = new CartItemSnapshot();
        item.setProductId(UUID.randomUUID());
        item.setProductName("Phone");
        item.setUnitPrice(100.0);
        item.setQty(1);

        CartSnapshot cart = new CartSnapshot();
        cart.setItems(List.of(item));
        cart.setTotalAmount(100.0);

        AddressDto address = new AddressDto();
        address.setCity("NYC");
        address.setState("NY");
        address.setCountry("US");
        address.setZipCode(10001);
        address.setHome("5A");
        address.setStreetLocality("Broadway");

        PaymentResponse payment = new PaymentResponse();
        payment.setId(PAYMENT_ID);
        payment.setStatus(PaymentStatus.INITIATED);

        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart);
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment);

        Order result = orderService.createOrder(USER, ADDRESS_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getOrderTotal()).isEqualTo(100.0);
        verify(cartFeignClient).clearCart(USER);
        verify(eventPublisher).publishOrderPlaced(any());
    }

    @Test
    void confirmOrder_success() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.confirmOrder(USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentFeignClient).confirmPayment(PAYMENT_ID);
        verify(eventPublisher).publishOrderConfirmed(any());
    }

    @Test
    void confirmOrder_notPending_throws() {
        pendingOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        assertThatThrownBy(() -> orderService.confirmOrder(USER, ORDER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cancelOrder_success() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(paymentFeignClient).refundPayment(PAYMENT_ID);
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test
    void cancelOrder_delivered_throws() {
        pendingOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        assertThatThrownBy(() -> orderService.cancelOrder(USER, ORDER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getOrdersByUser_returnsList() {
        when(orderRepository.findAllByUserId(USER)).thenReturn(List.of(pendingOrder));
        List<Order> orders = orderService.getOrdersByUser(USER);
        assertThat(orders).hasSize(1);
    }
}
