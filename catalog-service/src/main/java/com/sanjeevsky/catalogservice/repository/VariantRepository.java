package com.sanjeevsky.catalogservice.repository;

import com.sanjeevsky.catalogservice.model.Variant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VariantRepository extends JpaRepository<Variant, UUID> {
}
