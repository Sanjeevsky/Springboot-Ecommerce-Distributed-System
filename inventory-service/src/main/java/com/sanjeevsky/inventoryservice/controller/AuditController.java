package com.sanjeevsky.inventoryservice.controller;

import com.sanjeevsky.inventoryservice.model.AuditLog;
import com.sanjeevsky.inventoryservice.service.AuditService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory-service/audit")
public class AuditController {

    private final AuditService auditService;

    @AdminOnly
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLog>>> list(
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.list(entityId, page, size)));
    }
}
