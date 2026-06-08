package com.sanjeevsky.catalogservice.search;

import com.sanjeevsky.catalogservice.model.Product;
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

    @EventListener(ApplicationReadyEvent.class)
    public void indexAllProducts() {
        try {
            List<Product> products = productRepository.findAll();
            List<ProductDocument> docs = products.stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());
            searchRepository.saveAll(docs);
            log.info("Indexed {} products into OpenSearch", docs.size());
        } catch (Exception e) {
            log.warn("OpenSearch startup index skipped: {}", e.getMessage());
        }
    }

    public ProductDocument toDocument(Product p) {
        return ProductDocument.builder()
                .id(p.getId().toString())
                .name(p.getName() != null ? p.getName() : "")
                .description(p.getDescription() != null ? p.getDescription() : "")
                .brand(p.getBrand() != null ? p.getBrand().getName() : "")
                .brandId(p.getBrand() != null ? p.getBrand().getId().toString() : "")
                .categoryId(p.getCategory() != null ? p.getCategory().getId().toString() : "")
                .categoryName(p.getCategory() != null ? p.getCategory().getCategoryName() : "")
                .salePrice(p.getSalePrice())
                .mrpPrice(p.getMrpPrice())
                .status(p.getStatus())
                .build();
    }
}
