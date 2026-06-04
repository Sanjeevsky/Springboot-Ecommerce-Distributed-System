package com.sanjeevsky.orderservice.controller;

import com.sanjeevsky.orderservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.orderservice.model.Order;
import com.sanjeevsky.orderservice.service.OrderService;
import com.sanjeevsky.platform.model.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final String USER = "buyer@example.com";
    private static final UUID ADDRESS_ID = UUID.fromString("3b012a5e-32ff-452e-bcc2-deb30dbae60b");
    private static final UUID ORDER_ID = UUID.fromString("6b044edd-97ae-4b49-9e20-cdeb917ecc5c");
    private static final UUID PAYMENT_ID = UUID.fromString("0f223715-5857-4524-8df1-56cf2ecc9d9f");

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createOrder_missingAddressId_returns400() throws Exception {
        mockMvc.perform(post("/order-service/order")
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("addressId is required")));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withCouponAndIdempotencyKey_passesHeadersAndReturns201() throws Exception {
        when(orderService.createOrder(USER, ADDRESS_ID, "SAVE10", "order-1")).thenReturn(order(OrderStatus.PENDING));

        mockMvc.perform(post("/order-service/order")
                        .header("X-User", USER)
                        .header("Idempotency-Key", "order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + ADDRESS_ID + "\",\"couponCode\":\"SAVE10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order placed successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(orderService).createOrder(USER, ADDRESS_ID, "SAVE10", "order-1");
    }

    @Test
    void getOrder_passesUserHeaderAndReturnsOrder() throws Exception {
        when(orderService.getOrderById(USER, ORDER_ID)).thenReturn(order(OrderStatus.PENDING));

        mockMvc.perform(get("/order-service/order/{id}", ORDER_ID)
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()));

        verify(orderService).getOrderById(USER, ORDER_ID);
    }

    @Test
    void getOrders_passesUserHeaderAndReturnsOrders() throws Exception {
        when(orderService.getOrdersByUser(USER)).thenReturn(List.of(order(OrderStatus.PENDING)));

        mockMvc.perform(get("/order-service/orders")
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(ORDER_ID.toString()));

        verify(orderService).getOrdersByUser(USER);
    }

    @Test
    void confirmOrder_passesUserHeaderAndReturnsConfirmedOrder() throws Exception {
        when(orderService.confirmOrder(USER, ORDER_ID)).thenReturn(order(OrderStatus.CONFIRMED));

        mockMvc.perform(put("/order-service/order/{id}/confirm", ORDER_ID)
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order confirmed"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(orderService).confirmOrder(USER, ORDER_ID);
    }

    @Test
    void cancelOrder_passesUserHeaderAndReturnsCancelledOrder() throws Exception {
        when(orderService.cancelOrder(USER, ORDER_ID)).thenReturn(order(OrderStatus.CANCELLED));

        mockMvc.perform(put("/order-service/order/{id}/cancel", ORDER_ID)
                        .header("X-User", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order cancelled"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(orderService).cancelOrder(USER, ORDER_ID);
    }

    private Order order(OrderStatus status) {
        return Order.builder()
                .id(ORDER_ID)
                .userId(USER)
                .paymentId(PAYMENT_ID)
                .status(status)
                .orderTotal(119999)
                .discount(0)
                .shippingCharges(0)
                .build();
    }
}
