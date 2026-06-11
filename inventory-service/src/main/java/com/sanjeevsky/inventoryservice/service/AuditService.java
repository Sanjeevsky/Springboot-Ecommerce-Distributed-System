package com.sanjeevsky.inventoryservice.service;

import com.sanjeevsky.inventoryservice.model.AuditLog;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AuditService {

    /** Record an admin stock change. The actor is resolved from the request context. */
    void record(String entityType, UUID entityId, String action, String summary);

    /** List audit entries, newest first, optionally filtered to one entity. */
    Page<AuditLog> list(UUID entityId, int page, int size);
}
