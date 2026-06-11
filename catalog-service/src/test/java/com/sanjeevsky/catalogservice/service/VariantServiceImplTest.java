package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidVariantRequestException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.model.dto.VariantUpdateRequest;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.repository.VariantRepository;
import com.sanjeevsky.catalogservice.service.impl.VariantServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VariantServiceImplTest {

    @Mock private VariantRepository variantRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private VariantServiceImpl variantService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID VARIANT_ID = UUID.randomUUID();

    private Product product() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setName("Widget");
        return p;
    }

    private Variant variant() {
        Variant v = new Variant();
        v.setId(VARIANT_ID);
        v.setCondition1Name("Storage");
        v.setCondition1Value("256GB");
        v.setMrpPrice(100.0);
        v.setSalePrice(90.0);
        return v;
    }

    // ─── addVariant ────────────────────────────────────────────────────────────

    @Test
    void addVariant_productExists_savesAndReturnsVariant() {
        Product p = product();
        Variant v = variant();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));
        when(variantRepository.save(v)).thenReturn(v);

        Variant result = variantService.addVariant(PRODUCT_ID, v);

        assertThat(result).isSameAs(v);
        assertThat(v.getProduct()).isSameAs(p);
        assertThat(p.isHasVariant()).isTrue();
        verify(variantRepository).save(v);
        verify(productRepository).save(p);
    }

    @Test
    void addVariant_productNotFound_throwsProductNotExistsException() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.addVariant(PRODUCT_ID, variant()))
                .isInstanceOf(ProductNotExistsException.class);

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_nullProductId_throwsInvalidVariantRequestException() {
        assertThatThrownBy(() -> variantService.addVariant(null, variant()))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessage("Product id is required");

        verifyNoInteractions(productRepository, variantRepository);
    }

    @Test
    void addVariant_blankPrimaryConditionName_throwsInvalidVariantRequestException() {
        Variant v = variant();
        v.setCondition1Name(" ");

        assertThatThrownBy(() -> variantService.addVariant(PRODUCT_ID, v))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessageContaining("Primary condition name is required");

        verifyNoInteractions(productRepository, variantRepository);
    }

    @Test
    void addVariant_nonPositiveSalePrice_throwsInvalidVariantRequestException() {
        Variant v = variant();
        v.setSalePrice(0.0);

        assertThatThrownBy(() -> variantService.addVariant(PRODUCT_ID, v))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessageContaining("Sale price must be positive");

        verifyNoInteractions(productRepository, variantRepository);
    }

    @Test
    void addVariant_salePriceAboveMrp_throwsInvalidVariantRequestException() {
        Variant v = variant();
        v.setSalePrice(110.0);

        assertThatThrownBy(() -> variantService.addVariant(PRODUCT_ID, v))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessageContaining("Sale price cannot exceed MRP price");

        verifyNoInteractions(productRepository, variantRepository);
    }

    // ─── getVariant ────────────────────────────────────────────────────────────

    @Test
    void getVariant_exists_returnsVariant() {
        Variant v = variant();
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(v));

        Variant result = variantService.getVariant(VARIANT_ID);

        assertThat(result).isSameAs(v);
    }

    @Test
    void getVariant_notFound_throwsVariantNotExistsException() {
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.getVariant(VARIANT_ID))
                .isInstanceOf(VariantNotExistsException.class);
    }

    @Test
    void getVariant_nullId_throwsInvalidVariantRequestException() {
        assertThatThrownBy(() -> variantService.getVariant(null))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessage("Variant id is required");

        verifyNoInteractions(variantRepository);
    }

    @Test
    void updateVariant_partialRequest_mergesAndSaves() {
        Variant stored = variant();
        VariantUpdateRequest request = new VariantUpdateRequest();
        request.setCondition1Value(" 512GB ");
        request.setSalePrice(85.0);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(stored));
        when(variantRepository.save(stored)).thenReturn(stored);

        Variant result = variantService.updateVariant(VARIANT_ID, request);

        assertThat(result.getCondition1Value()).isEqualTo("512GB");
        assertThat(result.getSalePrice()).isEqualTo(85.0);
    }

    @Test
    void updateVariant_salePriceAboveMrp_throwsInvalidVariantRequestException() {
        Variant stored = variant();
        VariantUpdateRequest request = new VariantUpdateRequest();
        request.setSalePrice(101.0);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> variantService.updateVariant(VARIANT_ID, request))
                .isInstanceOf(InvalidVariantRequestException.class)
                .hasMessageContaining("Sale price cannot exceed MRP price");

        verify(variantRepository, never()).save(any());
    }

    @Test
    void deleteVariant_lastVariant_marksProductWithoutVariants() {
        Product product = product();
        Variant stored = variant();
        stored.setProduct(product);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(stored));
        when(variantRepository.countByProductId(PRODUCT_ID)).thenReturn(0L);

        variantService.deleteVariant(VARIANT_ID);

        verify(variantRepository).delete(stored);
        verify(variantRepository).flush();
        verify(productRepository, never()).save(any());
        assertThat(product.isHasVariant()).isFalse();
    }
}
