package com.sanjeevsky.catalogservice.search.repository;

import com.sanjeevsky.catalogservice.search.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}
