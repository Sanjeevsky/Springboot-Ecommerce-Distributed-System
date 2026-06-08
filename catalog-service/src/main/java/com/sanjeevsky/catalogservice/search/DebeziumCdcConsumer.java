package com.sanjeevsky.catalogservice.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DebeziumCdcConsumer {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final ProductDocumentMapper mapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "catalog.product-catalog-db.product", groupId = "catalog-es-sync")
    public void onProductChange(String message) {
        if (message == null) return; // tombstone record
        try {
            JsonNode root = objectMapper.readTree(message);
            // With schemas.enabled=false the root IS the payload; with schemas=true wrap is present
            JsonNode payload = root.has("schema") ? root.path("payload") : root;
            String op = payload.path("op").asText("");

            switch (op) {
                case "c": case "u": case "r":
                    handleUpsert(payload.path("after"));
                    break;
                case "d":
                    handleDelete(payload.path("before"));
                    break;
                default:
                    log.debug("Unhandled CDC op: {}", op);
            }
        } catch (Exception e) {
            log.warn("Failed to process CDC event: {}", e.getMessage());
        }
    }

    private void handleUpsert(JsonNode after) {
        String idStr = after.path("id").asText(null);
        if (idStr == null || idStr.isBlank()) return;
        try {
            UUID id = UUID.fromString(idStr);
            productRepository.findById(id).ifPresent(product -> {
                searchRepository.save(mapper.toDocument(product));
                log.debug("ES upsert: product {}", id);
            });
        } catch (IllegalArgumentException e) {
            log.warn("CDC upsert: invalid product id '{}': {}", idStr, e.getMessage());
        }
    }

    private void handleDelete(JsonNode before) {
        String idStr = before.path("id").asText(null);
        if (idStr == null || idStr.isBlank()) return;
        searchRepository.deleteById(idStr);
        log.debug("ES delete: product {}", idStr);
    }
}
