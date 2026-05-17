package com.sanjeevsky.catalogservice.repository;

import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByModelAndBrandId(String model, UUID brand);

    Page<Product> findAllByStatus(int status, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndStatus(String keyword, int status, Pageable pageable);

    Page<Product> findByCategoryIdAndStatus(UUID categoryId, int status, Pageable pageable);

    Page<Product> findByBrandIdAndStatus(UUID brandId, int status, Pageable pageable);

    Page<Product> findByCategoryIdAndBrandIdAndStatus(UUID categoryId, UUID brandId, int status, Pageable pageable);
}
