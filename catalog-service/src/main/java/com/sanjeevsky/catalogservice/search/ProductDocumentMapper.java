package com.sanjeevsky.catalogservice.search;

import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.search.document.ProductDocument;
import org.springframework.stereotype.Component;

@Component
public class ProductDocumentMapper {

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
