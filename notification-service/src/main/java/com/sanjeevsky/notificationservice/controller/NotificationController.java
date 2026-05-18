package com.sanjeevsky.notificationservice.controller;

import com.sanjeevsky.notificationservice.exceptions.NotificationNotFoundException;
import com.sanjeevsky.notificationservice.model.Notification;
import com.sanjeevsky.notificationservice.repository.NotificationRepository;
import com.sanjeevsky.platform.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/notification-service")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<Notification>>> getAllForUser(
            @RequestHeader("X-User") String userId) {
        log.info("Fetching all notifications for userId={}", userId);
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    @GetMapping("/notifications/unread")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnreadForUser(
            @RequestHeader("X-User") String userId) {
        log.info("Fetching unread notifications for userId={}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdAndRead(userId, false);
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markAsRead(@PathVariable UUID id) {
        log.info("Marking notification as read for id={}", id);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found with id: " + id));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", saved));
    }
}
