package com.sanjeevsky.inventoryservice.service;

import com.sanjeevsky.inventoryservice.exceptions.InsufficientStockException;
import com.sanjeevsky.inventoryservice.exceptions.InventoryNotFoundException;
import com.sanjeevsky.inventoryservice.model.Inventory;
import com.sanjeevsky.inventoryservice.model.InventoryTransaction;
import com.sanjeevsky.inventoryservice.repository.InventoryRepository;
import com.sanjeevsky.inventoryservice.repository.InventoryTransactionRepository;
import com.sanjeevsky.inventoryservice.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID VARIANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID INVENTORY_ID = UUID.randomUUID();

    private Inventory inventory(int total, int reserved) {
        return Inventory.builder()
                .id(INVENTORY_ID)
                .productId(PRODUCT_ID)
                .variantId(VARIANT_ID)
                .totalQty(total)
                .reservedQty(reserved)
                .build();
    }

    // ─── addStock ─────────────────────────────────────────────────────────────

    @Test
    void addStock_noExistingInventory_createsNewEntry() {
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> {
            Inventory i = inv.getArgument(0);
            i.setId(INVENTORY_ID);
            return i;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Inventory result = inventoryService.addStock(PRODUCT_ID, VARIANT_ID, 50);

        assertThat(result.getTotalQty()).isEqualTo(50);
        assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void addStock_existingInventory_incrementsTotalQty() {
        Inventory existing = inventory(30, 5);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Inventory result = inventoryService.addStock(PRODUCT_ID, VARIANT_ID, 20);

        assertThat(result.getTotalQty()).isEqualTo(50);
    }

    @Test
    void addStock_savesRestockTransaction() {
        Inventory existing = inventory(10, 0);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any())).thenReturn(existing);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.addStock(PRODUCT_ID, VARIANT_ID, 15);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("RESTOCK");
        assertThat(captor.getValue().getQuantity()).isEqualTo(15);
    }

    // ─── getStock ─────────────────────────────────────────────────────────────

    @Test
    void getStock_withVariant_returnsCorrectInventory() {
        Inventory inv = inventory(100, 20);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));

        Inventory result = inventoryService.getStock(PRODUCT_ID, VARIANT_ID);

        assertThat(result).isSameAs(inv);
    }

    @Test
    void getStock_withoutVariant_usesProductOnlyLookup() {
        Inventory inv = inventory(50, 10);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inv));

        Inventory result = inventoryService.getStock(PRODUCT_ID, null);

        assertThat(result).isSameAs(inv);
    }

    @Test
    void getStock_notFound_throwsInventoryNotFoundException() {
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getStock(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ─── getStockById ─────────────────────────────────────────────────────────

    @Test
    void getStockById_found_returnsInventory() {
        Inventory inv = inventory(40, 5);
        when(inventoryRepository.findById(INVENTORY_ID)).thenReturn(Optional.of(inv));

        Inventory result = inventoryService.getStockById(INVENTORY_ID);

        assertThat(result).isSameAs(inv);
    }

    @Test
    void getStockById_notFound_throwsInventoryNotFoundException() {
        when(inventoryRepository.findById(INVENTORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getStockById(INVENTORY_ID))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ─── reserveStock ─────────────────────────────────────────────────────────

    @Test
    void reserveStock_sufficientStock_increasesReservedQty() {
        Inventory inv = inventory(100, 10); // 90 available
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));
        when(inventoryRepository.save(inv)).thenReturn(inv);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.reserveStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 20);

        assertThat(result.getReservedQty()).isEqualTo(30);
        assertThat(result.getAvailableQty()).isEqualTo(70);
    }

    @Test
    void reserveStock_insufficientStock_throwsInsufficientStockException() {
        Inventory inv = inventory(10, 8); // only 2 available
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> inventoryService.reserveStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 5))
                .isInstanceOf(InsufficientStockException.class);

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveStock_savesReserveTransaction() {
        Inventory inv = inventory(50, 0);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));
        when(inventoryRepository.save(inv)).thenReturn(inv);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        inventoryService.reserveStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 10);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("RESERVE");
        assertThat(captor.getValue().getQuantity()).isEqualTo(10);
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
    }

    // ─── releaseStock ─────────────────────────────────────────────────────────

    @Test
    void releaseStock_decreasesReservedQty() {
        Inventory inv = inventory(50, 20);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));
        when(inventoryRepository.save(inv)).thenReturn(inv);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.releaseStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 10);

        assertThat(result.getReservedQty()).isEqualTo(10);
    }

    @Test
    void releaseStock_cannotGoBelowZero() {
        Inventory inv = inventory(50, 5);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));
        when(inventoryRepository.save(inv)).thenReturn(inv);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Inventory result = inventoryService.releaseStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 100);

        assertThat(result.getReservedQty()).isEqualTo(0);
    }

    @Test
    void releaseStock_savesReleaseTransaction() {
        Inventory inv = inventory(50, 15);
        when(inventoryRepository.findByProductIdAndVariantId(PRODUCT_ID, VARIANT_ID))
                .thenReturn(Optional.of(inv));
        when(inventoryRepository.save(inv)).thenReturn(inv);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        inventoryService.releaseStock(ORDER_ID, PRODUCT_ID, VARIANT_ID, 5);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("RELEASE");
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
    }

    // ─── getStockByProduct ────────────────────────────────────────────────────

    @Test
    void getStockByProduct_returnsAllVariants() {
        List<Inventory> all = List.of(inventory(10, 0), inventory(20, 5));
        when(inventoryRepository.findAllByProductId(PRODUCT_ID)).thenReturn(all);

        List<Inventory> result = inventoryService.getStockByProduct(PRODUCT_ID);

        assertThat(result).hasSize(2);
    }
}
