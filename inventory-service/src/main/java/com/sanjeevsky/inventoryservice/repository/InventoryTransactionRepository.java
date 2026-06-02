package com.sanjeevsky.inventoryservice.repository;

import com.sanjeevsky.inventoryservice.model.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    List<InventoryTransaction> findAllByOrderId(UUID orderId);

    boolean existsByOrderIdAndInventoryIdAndType(UUID orderId, UUID inventoryId, String type);
}
