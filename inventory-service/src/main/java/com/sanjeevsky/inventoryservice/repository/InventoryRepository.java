package com.sanjeevsky.inventoryservice.repository;

import com.sanjeevsky.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndVariantId(UUID productId, UUID variantId);

    Optional<Inventory> findByProductIdAndVariantIdIsNull(UUID productId);

    List<Inventory> findAllByProductId(UUID productId);
}
