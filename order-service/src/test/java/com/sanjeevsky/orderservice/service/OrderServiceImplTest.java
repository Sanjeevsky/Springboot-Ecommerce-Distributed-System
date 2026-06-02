package com.sanjeevsky.orderservice.service;

import com.sanjeevsky.orderservice.clients.CartFeignClient;
import com.sanjeevsky.orderservice.clients.CouponFeignClient;
import com.sanjeevsky.orderservice.clients.CustomerFeignClient;
import com.sanjeevsky.orderservice.clients.InventoryFeignClient;
import com.sanjeevsky.orderservice.clients.PaymentFeignClient;
import com.sanjeevsky.orderservice.events.OrderEventPublisher;
import com.sanjeevsky.orderservice.exceptions.InvalidRequestException;
import com.sanjeevsky.orderservice.exceptions.OrderNotFoundException;
import com.sanjeevsky.orderservice.model.AddressDto;
import com.sanjeevsky.orderservice.model.CouponValidationResult;
import com.sanjeevsky.orderservice.model.InventoryStock;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.model.OrderItem;
import com.sanjeevsky.orderservice.model.ShippingAddress;
import com.sanjeevsky.orderservice.repository.OrderRepository;
import com.sanjeevsky.orderservice.service.impl.OrderServiceImpl;
import com.sanjeevsky.platform.events.OrderConfirmedEvent;
import com.sanjeevsky.platform.model.cart.CartItemSnapshot;
import com.sanjeevsky.platform.model.cart.CartSnapshot;
import com.sanjeevsky.platform.model.order.OrderStatus;
import com.sanjeevsky.platform.model.payment.PaymentRequest;
import com.sanjeevsky.platform.model.payment.PaymentResponse;
import com.sanjeevsky.platform.model.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock CartFeignClient cartFeignClient;
    @Mock PaymentFeignClient paymentFeignClient;
    @Mock CustomerFeignClient customerFeignClient;
    @Mock CouponFeignClient couponFeignClient;
    @Mock InventoryFeignClient inventoryFeignClient;
    @Mock OrderEventPublisher eventPublisher;

    @InjectMocks OrderServiceImpl orderService;

    private static final String USER = "user@example.com";
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        lenient().when(inventoryFeignClient.getStockByProduct(any())).thenReturn(null);
        pendingOrder = Order.builder()
                .id(ORDER_ID)
                .userId(USER)
                .status(OrderStatus.PENDING)
                .paymentId(PAYMENT_ID)
                .shippingAddress(ShippingAddress.builder().city("NYC").build())
                .orderItems(List.of(OrderItem.builder()
                        .productId(UUID.randomUUID())
                        .variantId(UUID.randomUUID())
                        .productName("Phone")
                        .unitPrice(100.0)
                        .qty(1)
                        .build()))
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
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(new CartSnapshot());
        assertThatThrownBy(() -> orderService.createOrder(USER, ADDRESS_ID, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void createOrder_success() {
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart(List.of(cartItem(100.0)), 100.0));
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment());

        Order result = orderService.createOrder(USER, ADDRESS_ID, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getOrderTotal()).isEqualTo(100.0);
        verify(cartFeignClient).clearCart(USER);
        verify(eventPublisher).publishOrderPlaced(any());
    }

    @Test
    void createOrder_insufficientInventory_throwsBeforePayment() {
        CartItemSnapshot item = cartItem(100.0);
        item.setQty(2);
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart(List.of(item), 200.0));
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address());
        when(inventoryFeignClient.getStockByProduct(item.getProductId()))
                .thenReturn(List.of(stock(item.getProductId(), item.getVariantId(), 1, 0)));

        assertThatThrownBy(() -> orderService.createOrder(USER, ADDRESS_ID, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Insufficient stock");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentFeignClient, eventPublisher);
    }

    @Test
    void createOrder_inventoryUnavailable_continuesWithKafkaReservation() {
        CartItemSnapshot item = cartItem(100.0);
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart(List.of(item), 100.0));
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address());
        when(inventoryFeignClient.getStockByProduct(item.getProductId())).thenReturn(null);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment());

        Order result = orderService.createOrder(USER, ADDRESS_ID, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(eventPublisher).publishOrderPlaced(any());
    }

    @Test
    void createOrder_usesMatchingVariantInventory() {
        CartItemSnapshot item = cartItem(100.0);
        UUID otherVariant = UUID.randomUUID();
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart(List.of(item), 100.0));
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address());
        when(inventoryFeignClient.getStockByProduct(item.getProductId()))
                .thenReturn(List.of(
                        stock(item.getProductId(), otherVariant, 100, 100),
                        stock(item.getProductId(), item.getVariantId(), 2, 0)
                ));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment());

        Order result = orderService.createOrder(USER, ADDRESS_ID, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(eventPublisher).publishOrderPlaced(any());
    }

    @Test
    void createOrder_withIdempotencyKey_storesKeyAndPropagatesPaymentKey() {
        when(orderRepository.findByUserIdAndIdempotencyKey(USER, "checkout-1")).thenReturn(Optional.empty());
        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart(List.of(cartItem(100.0)), 100.0));
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment());

        Order result = orderService.createOrder(USER, ADDRESS_ID, null, " checkout-1 ");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        ArgumentCaptor<PaymentRequest> paymentRequestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());
        verify(paymentFeignClient).initiatePayment(paymentRequestCaptor.capture());

        assertThat(result.getIdempotencyKey()).isEqualTo("checkout-1");
        assertThat(orderCaptor.getAllValues().get(0).getIdempotencyKey()).isEqualTo("checkout-1");
        assertThat(paymentRequestCaptor.getValue().getIdempotencyKey()).isEqualTo("order:checkout-1");
    }

    @Test
    void createOrder_withSameIdempotencyKey_returnsExistingOrderWithoutSideEffects() {
        pendingOrder.setIdempotencyKey("checkout-1");
        when(orderRepository.findByUserIdAndIdempotencyKey(USER, "checkout-1"))
                .thenReturn(Optional.of(pendingOrder));

        Order result = orderService.createOrder(USER, ADDRESS_ID, null, "checkout-1");

        assertThat(result).isSameAs(pendingOrder);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(cartFeignClient, customerFeignClient, couponFeignClient, paymentFeignClient, eventPublisher);
    }

    @Test
    void createOrder_withSameIdempotencyKey_completesExistingOrderWithoutPayment() {
        pendingOrder.setIdempotencyKey("checkout-1");
        pendingOrder.setPaymentId(null);
        pendingOrder.setOrderItems(List.of(OrderItem.builder()
                .productId(UUID.randomUUID())
                .productName("Phone")
                .unitPrice(100.0)
                .qty(1)
                .build()));
        when(orderRepository.findByUserIdAndIdempotencyKey(USER, "checkout-1"))
                .thenReturn(Optional.of(pendingOrder));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(USER, ADDRESS_ID, null, "checkout-1");

        assertThat(result.getPaymentId()).isEqualTo(PAYMENT_ID);
        verify(paymentFeignClient).initiatePayment(any());
        verify(orderRepository).save(pendingOrder);
        verify(cartFeignClient).clearCart(USER);
        verify(eventPublisher).publishOrderPlaced(any());
        verify(cartFeignClient, never()).getCheckoutSnapshot(USER);
        verifyNoInteractions(customerFeignClient, couponFeignClient);
    }

    @Test
    void confirmOrder_success() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.confirmOrder(USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentFeignClient).confirmPayment(PAYMENT_ID);
        ArgumentCaptor<OrderConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(eventPublisher).publishOrderConfirmed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getItems()).hasSize(1);
    }

    @Test
    void confirmOrder_alreadyConfirmed_isIdempotent() {
        pendingOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));

        Order result = orderService.confirmOrder(USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentFeignClient, eventPublisher);
    }

    @Test
    void confirmOrder_cancelled_throws() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
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
    void cancelOrder_alreadyCancelled_returnsExistingOrderWithoutSideEffects() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER)).thenReturn(Optional.of(pendingOrder));

        Order result = orderService.cancelOrder(USER, ORDER_ID);

        assertThat(result).isSameAs(pendingOrder);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentFeignClient, eventPublisher);
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

    // ─── Coupon integration ───────────────────────────────────────────────────

    @Test
    void createOrder_validCoupon_appliesDiscount() {
        CartItemSnapshot item = cartItem(100.0);
        CartSnapshot cart = cart(List.of(item), 100.0);
        AddressDto address = address();
        PaymentResponse payment = payment();

        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart);
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment);
        when(couponFeignClient.validateCoupon("SAVE10", 100.0))
                .thenReturn(new CouponValidationResult(true, "Valid", 10.0, "SAVE10"));

        Order result = orderService.createOrder(USER, ADDRESS_ID, "SAVE10");

        assertThat(result.getOrderTotal()).isEqualTo(90.0);
        assertThat(result.getDiscount()).isEqualTo(10.0);
        verify(couponFeignClient).applyCoupon("SAVE10");
    }

    @Test
    void createOrder_invalidCoupon_noDiscount() {
        CartItemSnapshot item = cartItem(100.0);
        CartSnapshot cart = cart(List.of(item), 100.0);
        AddressDto address = address();
        PaymentResponse payment = payment();

        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart);
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment);
        when(couponFeignClient.validateCoupon("BADCODE", 100.0))
                .thenReturn(new CouponValidationResult(false, "Expired", 0.0, "BADCODE"));

        Order result = orderService.createOrder(USER, ADDRESS_ID, "BADCODE");

        assertThat(result.getOrderTotal()).isEqualTo(100.0);
        assertThat(result.getDiscount()).isEqualTo(0.0);
        verify(couponFeignClient, org.mockito.Mockito.never()).applyCoupon(any());
    }

    @Test
    void createOrder_nullCoupon_skipsValidation() {
        CartItemSnapshot item = cartItem(100.0);
        CartSnapshot cart = cart(List.of(item), 100.0);
        AddressDto address = address();
        PaymentResponse payment = payment();

        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart);
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment);

        Order result = orderService.createOrder(USER, ADDRESS_ID, null);

        assertThat(result.getOrderTotal()).isEqualTo(100.0);
        verify(couponFeignClient, org.mockito.Mockito.never()).validateCoupon(any(), anyDouble());
    }

    @Test
    void createOrder_couponServiceDown_fallbackNoDiscount() {
        CartItemSnapshot item = cartItem(100.0);
        CartSnapshot cart = cart(List.of(item), 100.0);
        AddressDto address = address();
        PaymentResponse payment = payment();

        when(cartFeignClient.getCheckoutSnapshot(USER)).thenReturn(cart);
        when(customerFeignClient.getAddress(USER, ADDRESS_ID)).thenReturn(address);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFeignClient.initiatePayment(any())).thenReturn(payment);
        // fallback returns valid=false
        when(couponFeignClient.validateCoupon("SAVE10", 100.0))
                .thenReturn(new CouponValidationResult(false, "Coupon service unavailable", 0.0, "SAVE10"));

        Order result = orderService.createOrder(USER, ADDRESS_ID, "SAVE10");

        assertThat(result.getOrderTotal()).isEqualTo(100.0);
        verify(couponFeignClient, org.mockito.Mockito.never()).applyCoupon(any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CartItemSnapshot cartItem(double price) {
        CartItemSnapshot item = new CartItemSnapshot();
        item.setProductId(UUID.randomUUID());
        item.setProductName("Phone");
        item.setUnitPrice(price);
        item.setQty(1);
        return item;
    }

    private CartSnapshot cart(List<CartItemSnapshot> items, double total) {
        CartSnapshot c = new CartSnapshot();
        c.setItems(items);
        c.setTotalAmount(total);
        return c;
    }

    private InventoryStock stock(UUID productId, UUID variantId, int totalQty, int reservedQty) {
        InventoryStock stock = new InventoryStock();
        stock.setProductId(productId);
        stock.setVariantId(variantId);
        stock.setTotalQty(totalQty);
        stock.setReservedQty(reservedQty);
        stock.setAvailableQty(totalQty - reservedQty);
        return stock;
    }

    private AddressDto address() {
        AddressDto a = new AddressDto();
        a.setCity("NYC"); a.setState("NY"); a.setCountry("US");
        a.setZipCode(10001); a.setHome("5A"); a.setStreetLocality("Broadway");
        return a;
    }

    private PaymentResponse payment() {
        PaymentResponse p = new PaymentResponse();
        p.setId(PAYMENT_ID);
        p.setStatus(PaymentStatus.PENDING);
        return p;
    }
}
