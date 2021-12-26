package com.sanjeevsky.catalogservice.repository;

import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByModelAndBrandId(String model, UUID brand);
}
