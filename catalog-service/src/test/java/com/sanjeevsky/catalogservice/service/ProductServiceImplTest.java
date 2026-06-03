package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.NoSuchProductExistsException;
import com.sanjeevsky.catalogservice.exceptions.InvalidProductRequestException;
import com.sanjeevsky.catalogservice.exceptions.ProductAlreadyExistsException;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.model.Category;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.SubCategory;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private BrandService brandService;
    @Mock private CategoryService categoryService;
    @Mock private SubCategoryService subCategoryService;

    @InjectMocks
    private ProductServiceImpl productService;

    private static final UUID PRODUCT_ID  = UUID.randomUUID();
    private static final UUID BRAND_ID    = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SUB_ID      = UUID.randomUUID();

    private Product product(String name) {
        return Product.builder()
                .id(PRODUCT_ID)
                .name(name)
                .model("M1")
                .mrpPrice(100.0)
                .salePrice(90.0)
                .gstValue(18.0)
                .discount(10.0)
                .status(1)
                .build();
    }

    // ─── addProduct ───────────────────────────────────────────────────────────

    @Test
    void addProduct_newModel_savesProduct() {
        when(productRepository.findByModelAndBrandId("M1", BRAND_ID)).thenReturn(Optional.empty());
        when(brandService.getBrand(BRAND_ID)).thenReturn(Brand.builder().id(BRAND_ID).build());
        when(categoryService.getCategory(CATEGORY_ID)).thenReturn(Category.builder().id(CATEGORY_ID).build());
        when(subCategoryService.getSubCategory(SUB_ID)).thenReturn(SubCategory.builder().id(SUB_ID).build());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product p = product("Widget");
        Product result = productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, p);

        verify(productRepository).save(p);
        assertThat(result).isSameAs(p);
    }

    @Test
    void addProduct_duplicateModelAndBrand_throwsProductAlreadyExistsException() {
        when(productRepository.findByModelAndBrandId("M1", BRAND_ID))
                .thenReturn(Optional.of(product("Widget")));

        assertThatThrownBy(() -> productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, product("Widget")))
                .isInstanceOf(ProductAlreadyExistsException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void addProduct_blankName_throwsInvalidProductRequestException() {
        Product p = product(" ");

        assertThatThrownBy(() -> productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, p))
                .isInstanceOf(InvalidProductRequestException.class)
                .hasMessageContaining("Product name is required");

        verifyNoInteractions(productRepository, brandService, categoryService, subCategoryService);
    }

    @Test
    void addProduct_salePriceAboveMrp_throwsInvalidProductRequestException() {
        Product p = product("Widget");
        p.setSalePrice(110.0);

        assertThatThrownBy(() -> productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, p))
                .isInstanceOf(InvalidProductRequestException.class)
                .hasMessageContaining("Sale price cannot exceed MRP price");

        verifyNoInteractions(productRepository, brandService, categoryService, subCategoryService);
    }

    @Test
    void addProduct_negativeDiscount_throwsInvalidProductRequestException() {
        Product p = product("Widget");
        p.setDiscount(-1.0);

        assertThatThrownBy(() -> productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, p))
                .isInstanceOf(InvalidProductRequestException.class)
                .hasMessageContaining("Discount must not be negative");

        verifyNoInteractions(productRepository, brandService, categoryService, subCategoryService);
    }

    @Test
    void addProduct_invalidStatus_throwsInvalidProductRequestException() {
        Product p = product("Widget");
        p.setStatus(2);

        assertThatThrownBy(() -> productService.addProduct(BRAND_ID, CATEGORY_ID, SUB_ID, p))
                .isInstanceOf(InvalidProductRequestException.class)
                .hasMessageContaining("Status must be 0 or 1");

        verifyNoInteractions(productRepository, brandService, categoryService, subCategoryService);
    }

    // ─── getProduct ───────────────────────────────────────────────────────────

    @Test
    void getProduct_exists_returnsProduct() {
        Product p = product("Widget");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));

        assertThat(productService.getProduct(PRODUCT_ID)).isSameAs(p);
    }

    @Test
    void getProduct_notFound_throwsNoSuchProductExistsException() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_ID))
                .isInstanceOf(NoSuchProductExistsException.class);
    }

    // ─── listProducts ─────────────────────────────────────────────────────────

    @Test
    void listProducts_returnsActivePage() {
        Page<Product> page = new PageImpl<>(List.of(product("Widget")));
        when(productRepository.findAllByStatus(eq(1), any(Pageable.class))).thenReturn(page);

        Page<Product> result = productService.listProducts(0, 10, "name");

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── searchProducts ───────────────────────────────────────────────────────

    @Test
    void searchProducts_keywordOnly_passesKeywordThenNullFilters() {
        Page<Product> page = new PageImpl<>(Collections.emptyList());
        when(productRepository.search(eq("phone"), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        productService.searchProducts("phone", null, null, 0, 20);

        verify(productRepository).search(eq("phone"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void searchProducts_blankKeyword_treatedAsNull() {
        Page<Product> page = new PageImpl<>(Collections.emptyList());
        when(productRepository.search(isNull(), eq(CATEGORY_ID), isNull(), any(Pageable.class))).thenReturn(page);

        productService.searchProducts("  ", CATEGORY_ID, null, 0, 20);

        verify(productRepository).search(isNull(), eq(CATEGORY_ID), isNull(), any(Pageable.class));
    }

    @Test
    void searchProducts_noFilters_passesAllNulls() {
        Page<Product> page = new PageImpl<>(Collections.emptyList());
        when(productRepository.search(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        productService.searchProducts("", null, null, 0, 20);

        verify(productRepository).search(isNull(), isNull(), isNull(), any(Pageable.class));
    }
}
