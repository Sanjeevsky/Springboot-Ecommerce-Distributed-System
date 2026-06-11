package com.sanjeevsky.inventoryservice.service.impl;

import com.sanjeevsky.inventoryservice.model.AuditLog;
import com.sanjeevsky.inventoryservice.repository.AuditLogRepository;
import com.sanjeevsky.inventoryservice.service.AuditService;
import com.sanjeevsky.platform.mdc.MdcConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository repository;

    @Override
    public void record(String entityType, UUID entityId, String action, String summary) {
        try {
            repository.save(AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .actor(currentActor())
                    .summary(summary)
                    .build());
        } catch (Exception e) {
            // Auditing must never break the mutation it records.
            log.warn("Failed to write inventory audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    @Override
    public Page<AuditLog> list(UUID entityId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size);
        return entityId == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByEntityIdOrderByCreatedAtDesc(entityId, pageable);
    }

    private String currentActor() {
        String actor = MDC.get(MdcConstants.USER_ID);
        return actor == null || actor.trim().isEmpty() ? SYSTEM_ACTOR : actor.trim();
    }
}
