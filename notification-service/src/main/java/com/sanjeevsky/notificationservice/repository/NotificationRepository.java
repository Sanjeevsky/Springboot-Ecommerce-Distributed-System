package com.sanjeevsky.notificationservice.repository;

import com.sanjeevsky.notificationservice.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserId(String userId);

    List<Notification> findByUserIdAndRead(String userId, boolean read);

    boolean existsByEventKey(String eventKey);
}
