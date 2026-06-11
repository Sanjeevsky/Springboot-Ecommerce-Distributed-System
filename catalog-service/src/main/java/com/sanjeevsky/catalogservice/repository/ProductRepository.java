package com.sanjeevsky.catalogservice.repository;

import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByModelAndBrandId(String model, UUID brand);

    Page<Product> findAllByStatus(int status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE " +
           "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.model) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR p.status = :status)")
    Page<Product> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 1 " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:brandId IS NULL OR p.brand.id = :brandId)")
    Page<Product> search(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            Pageable pageable);
}
