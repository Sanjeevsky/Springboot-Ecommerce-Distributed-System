package com.sanjeevsky.customerservice.service;

import com.sanjeevsky.customerservice.clients.CartFeignClient;
import com.sanjeevsky.customerservice.clients.PaymentFeignClient;
import com.sanjeevsky.customerservice.exceptions.AddressDoesnotExistsException;
import com.sanjeevsky.customerservice.exceptions.InvalidRequestException;
import com.sanjeevsky.customerservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.customerservice.model.Address;
import com.sanjeevsky.customerservice.model.Order;
import com.sanjeevsky.customerservice.repository.AddressRepository;
import com.sanjeevsky.customerservice.repository.OrderRepository;
import com.sanjeevsky.customerservice.service.impl.OrderServiceImpl;
import com.sanjeevsky.platform.model.cart.CartItemSnapshot;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import com.sanjeevsky.platform.model.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CartFeignClient cartFeignClient;
    @Mock private PaymentFeignClient paymentFeignClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final String USER_EMAIL = "buyer@example.com";
    private static final UUID ADDRESS_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private CartItemSnapshot cartItem(double price, int qty) {
        return new CartItemSnapshot(UUID.randomUUID(), UUID.randomUUID(), null, "Widget", price, qty);
    }

    private CartSnapshot cartWithItems(CartItemSnapshot... items) {
        return new CartSnapshot(UUID.randomUUID(), USER_EMAIL, List.of(items),
                List.of(items).stream().mapToDouble(i -> i.getUnitPrice() * i.getQty()).sum());
    }

    private Address address() {
        Address a = new Address();
        a.setId(ADDRESS_ID);
        a.setUser(USER_EMAIL);
        a.setCity("Bangalore");
        return a;
    }

    private PaymentResponse pendingPayment() {
        return new PaymentResponse(PAYMENT_ID, ORDER_ID, USER_EMAIL, 100.0, "INR", PaymentStatus.PENDING);
    }

    // ─── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_happyPath_createsOrderAndInitiatesPaymentAndClearsCart() {
        CartItemSnapshot item = cartItem(50.0, 2);   // total = 100
        when(cartFeignClient.getCheckoutSnapshot(USER_EMAIL)).thenReturn(cartWithItems(item));
        when(addressRepository.findByIdAndUser(ADDRESS_ID, USER_EMAIL)).thenReturn(Optional.of(address()));

        // first save returns order with id, second save (after paymentId set) returns same
        Order savedOrder = Order.builder()
                .id(ORDER_ID).userId(USER_EMAIL).status(OrderStatus.PENDING).orderTotal(100.0)
                .orderItems(new ArrayList<>()).build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentFeignClient.initiatePayment(any(PaymentRequest.class))).thenReturn(pendingPayment());

        Order result = orderService.createOrder(USER_EMAIL, ADDRESS_ID);

        assertThat(result).isNotNull();
        verify(paymentFeignClient).initiatePayment(any(PaymentRequest.class));
        verify(cartFeignClient).clearCart(USER_EMAIL);
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    void createOrder_emptyCart_throwsInvalidRequestException() {
        CartSnapshot emptyCart = new CartSnapshot(UUID.randomUUID(), USER_EMAIL, Collections.emptyList(), 0.0);
        when(cartFeignClient.getCheckoutSnapshot(USER_EMAIL)).thenReturn(emptyCart);

        assertThatThrownBy(() -> orderService.createOrder(USER_EMAIL, ADDRESS_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("empty");

        verifyNoInteractions(orderRepository, paymentFeignClient);
    }

    @Test
    void createOrder_nullItems_throwsInvalidRequestException() {
        CartSnapshot cartWithNullItems = new CartSnapshot(UUID.randomUUID(), USER_EMAIL, null, 0.0);
        when(cartFeignClient.getCheckoutSnapshot(USER_EMAIL)).thenReturn(cartWithNullItems);

        assertThatThrownBy(() -> orderService.createOrder(USER_EMAIL, ADDRESS_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createOrder_addressNotFound_throwsAddressDoesnotExistsException() {
        CartItemSnapshot item = cartItem(10.0, 1);
        when(cartFeignClient.getCheckoutSnapshot(USER_EMAIL)).thenReturn(cartWithItems(item));
        when(addressRepository.findByIdAndUser(ADDRESS_ID, USER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(USER_EMAIL, ADDRESS_ID))
                .isInstanceOf(AddressDoesnotExistsException.class);

        verifyNoInteractions(orderRepository, paymentFeignClient);
    }

    @Test
    void createOrder_orderItemsSnapshotPriceFromCart() {
        CartItemSnapshot item = cartItem(75.0, 3);  // total = 225
        when(cartFeignClient.getCheckoutSnapshot(USER_EMAIL)).thenReturn(cartWithItems(item));
        when(addressRepository.findByIdAndUser(ADDRESS_ID, USER_EMAIL)).thenReturn(Optional.of(address()));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        Order savedOrder = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.PENDING).orderTotal(225.0).orderItems(new ArrayList<>()).build();
        when(orderRepository.save(orderCaptor.capture())).thenReturn(savedOrder);
        when(paymentFeignClient.initiatePayment(any())).thenReturn(pendingPayment());

        orderService.createOrder(USER_EMAIL, ADDRESS_ID);

        Order firstSave = orderCaptor.getAllValues().get(0);
        assertThat(firstSave.getOrderTotal()).isEqualTo(225.0);
        assertThat(firstSave.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(firstSave.getUserId()).isEqualTo(USER_EMAIL);
    }

    // ─── confirmOrder ─────────────────────────────────────────────────────────

    @Test
    void confirmOrder_pendingOrder_confirmsAndCallsPayment() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.PENDING).paymentId(PAYMENT_ID).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.confirmOrder(USER_EMAIL, ORDER_ID);

        verify(paymentFeignClient).confirmPayment(PAYMENT_ID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirmOrder_notPendingOrder_throwsInvalidRequestException() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmOrder(USER_EMAIL, ORDER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("PENDING");

        verifyNoInteractions(paymentFeignClient);
    }

    @Test
    void confirmOrder_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(USER_EMAIL, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ─── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_cancelsAndRefunds() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.PENDING).paymentId(PAYMENT_ID).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(USER_EMAIL, ORDER_ID);

        verify(paymentFeignClient).refundPayment(PAYMENT_ID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_noPaymentId_cancelsWithoutRefund() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.PENDING).paymentId(null).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(USER_EMAIL, ORDER_ID);

        verifyNoInteractions(paymentFeignClient);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_deliveredOrder_throwsInvalidRequestException() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.DELIVERED).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(USER_EMAIL, ORDER_ID))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(paymentFeignClient);
    }

    @Test
    void cancelOrder_alreadyCancelled_throwsInvalidRequestException() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL)
                .status(OrderStatus.CANCELLED).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(USER_EMAIL, ORDER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ─── getOrderById ──────────────────────────────────────────────────────────

    @Test
    void getOrderById_exists_returnsOrder() {
        Order order = Order.builder().id(ORDER_ID).userId(USER_EMAIL).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(USER_EMAIL, ORDER_ID);

        assertThat(result).isSameAs(order);
    }

    @Test
    void getOrderById_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(USER_EMAIL, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(ORDER_ID.toString());
    }

    // ─── getOrdersByUser ───────────────────────────────────────────────────────

    @Test
    void getOrdersByUser_returnsAllOrdersForUser() {
        List<Order> orders = List.of(
                Order.builder().id(UUID.randomUUID()).userId(USER_EMAIL).build(),
                Order.builder().id(UUID.randomUUID()).userId(USER_EMAIL).build()
        );
        when(orderRepository.findAllByUserId(USER_EMAIL)).thenReturn(orders);

        List<Order> result = orderService.getOrdersByUser(USER_EMAIL);

        assertThat(result).hasSize(2);
        verify(orderRepository).findAllByUserId(USER_EMAIL);
    }

    @Test
    void getOrdersByUser_noOrders_returnsEmptyList() {
        when(orderRepository.findAllByUserId(USER_EMAIL)).thenReturn(Collections.emptyList());

        List<Order> result = orderService.getOrdersByUser(USER_EMAIL);

        assertThat(result).isEmpty();
    }
}
