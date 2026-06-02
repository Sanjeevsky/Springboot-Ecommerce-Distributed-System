package com.sanjeevsky.notificationservice.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(name = "event_key")
    private String eventKey;

    private String type;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "read_flag")
    private boolean read = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
