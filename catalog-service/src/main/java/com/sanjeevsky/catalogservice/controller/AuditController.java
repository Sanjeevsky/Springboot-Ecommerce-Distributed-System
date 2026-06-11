package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.AuditLog;
import com.sanjeevsky.catalogservice.service.AuditService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/catalog-service/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @AdminOnly
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLog>>> list(
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.list(entityId, page, size)));
    }
}
