package com.sanjeevsky.notificationservice.controller;

import com.sanjeevsky.notificationservice.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String USER_ID = "buyer@example.com";
    private static final UUID NOTIFICATION_ID = UUID.fromString("ab139bf2-2566-4682-b8f5-5ea6df316cff");

    @Mock
    private NotificationRepository notificationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllForUser_trimsXUserAndReturnsNotifications() throws Exception {
        when(notificationRepository.findByUserId(USER_ID)).thenReturn(List.of(notification(false)));

        mockMvc.perform(get("/notification-service/notifications")
                        .header("X-User", " " + USER_ID + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(NOTIFICATION_ID.toString()));

        verify(notificationRepository).findByUserId(USER_ID);
    }

    @Test
    void getUnreadForUser_trimsXUserAndQueriesUnreadOnly() throws Exception {
        when(notificationRepository.findByUserIdAndRead(USER_ID, false)).thenReturn(List.of(notification(false)));

        mockMvc.perform(get("/notification-service/notifications/unread")
                        .header("X-User", " " + USER_ID + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].read").value(false));

        verify(notificationRepository).findByUserIdAndRead(USER_ID, false);
    }

    @Test
    void getAllForUser_blankXUser_returns400BeforeRepositoryCall() throws Exception {
        mockMvc.perform(get("/notification-service/notifications")
                        .header("X-User", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Notification userId is required"));

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markAsRead_marksNotificationAndReturnsSavedEntity() throws Exception {
        Notification notification = notification(false);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        mockMvc.perform(put("/notification-service/notifications/{id}/read", NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification marked as read"))
                .andExpect(jsonPath("$.data.read").value(true));

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).findById(NOTIFICATION_ID);
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_missingNotification_returns404BeforeSave() throws Exception {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/notification-service/notifications/{id}/read", NOTIFICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Notification not found with id: " + NOTIFICATION_ID));

        verify(notificationRepository).findById(NOTIFICATION_ID);
        verify(notificationRepository, never()).save(any());
    }

    private Notification notification(boolean read) {
        return Notification.builder()
                .id(NOTIFICATION_ID)
                .userId(USER_ID)
                .eventKey("order-" + NOTIFICATION_ID)
                .type("ORDER_CONFIRMED")
                .subject("Order confirmed")
                .message("Your order was confirmed")
                .read(read)
                .build();
    }
}
