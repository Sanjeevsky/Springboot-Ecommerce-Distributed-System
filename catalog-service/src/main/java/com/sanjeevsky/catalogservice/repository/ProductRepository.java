package com.sanjeevsky.catalogservice.repository;

import com.sanjeevsky.catalogservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByModelAndBrandId(String model, long brand);
}
