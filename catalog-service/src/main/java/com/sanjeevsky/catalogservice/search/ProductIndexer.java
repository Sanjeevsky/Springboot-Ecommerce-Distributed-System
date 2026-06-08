package com.sanjeevsky.catalogservice.search;

import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.search.document.ProductDocument;
import com.sanjeevsky.catalogservice.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexer {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final ProductDocumentMapper mapper;

    @EventListener(ApplicationReadyEvent.class)
    public void indexAllProducts() {
        try {
            List<ProductDocument> docs = productRepository.findAll().stream()
                    .map(mapper::toDocument)
                    .collect(Collectors.toList());
            searchRepository.saveAll(docs);
            log.info("Indexed {} products into OpenSearch", docs.size());
        } catch (Exception e) {
            log.warn("OpenSearch startup index skipped: {}", e.getMessage());
        }
    }
}
