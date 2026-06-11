package com.sanjeevsky.inventoryservice.service;

import com.sanjeevsky.inventoryservice.model.AuditLog;
import com.sanjeevsky.inventoryservice.repository.AuditLogRepository;
import com.sanjeevsky.inventoryservice.service.impl.AuditServiceImpl;
import com.sanjeevsky.platform.mdc.MdcConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditServiceImpl auditService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void record_savesEntryWithActorFromMdc() {
        MDC.put(MdcConstants.USER_ID, "admin@trove.local");
        UUID productId = UUID.randomUUID();

        auditService.record("STOCK", productId, "STOCK_SET", "Stock set 5 -> 20");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("STOCK");
        assertThat(saved.getEntityId()).isEqualTo(productId);
        assertThat(saved.getAction()).isEqualTo("STOCK_SET");
        assertThat(saved.getActor()).isEqualTo("admin@trove.local");
        assertThat(saved.getSummary()).isEqualTo("Stock set 5 -> 20");
    }

    @Test
    void record_noMdcActor_fallsBackToSystem() {
        auditService.record("STOCK", UUID.randomUUID(), "RESTOCK", "Restocked +10");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("system");
    }

    @Test
    void record_repositoryFailure_isSwallowed() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        auditService.record("STOCK", UUID.randomUUID(), "RESTOCK", "Restocked +10");
    }

    @Test
    void list_noEntityId_queriesAllNewestFirst() {
        Page<AuditLog> page = new PageImpl<>(Collections.emptyList());
        when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20))).thenReturn(page);

        assertThat(auditService.list(null, 0, 20)).isSameAs(page);
        verify(repository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20));
    }

    @Test
    void list_withEntityId_filtersByEntity() {
        UUID id = UUID.randomUUID();
        Page<AuditLog> page = new PageImpl<>(Collections.emptyList());
        when(repository.findByEntityIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))).thenReturn(page);

        assertThat(auditService.list(id, 0, 20)).isSameAs(page);
        verify(repository).findByEntityIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20));
    }

    @Test
    void list_invalidPageSize_throws() {
        assertThatThrownBy(() -> auditService.list(null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
