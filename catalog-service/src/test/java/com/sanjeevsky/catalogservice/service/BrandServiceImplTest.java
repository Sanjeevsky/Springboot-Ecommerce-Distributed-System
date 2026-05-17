package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.BrandAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.repository.BrandRepository;
import com.sanjeevsky.catalogservice.service.impl.BrandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandServiceImpl brandService;

    private static final UUID BRAND_ID = UUID.randomUUID();

    private Brand brand(String name) {
        return Brand.builder().id(BRAND_ID).name(name).build();
    }

    // ─── getBrand ─────────────────────────────────────────────────────────────

    @Test
    void getBrand_exists_returnsBrand() {
        Brand b = brand("Nike");
        when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(b));

        Brand result = brandService.getBrand(BRAND_ID);

        assertThat(result).isSameAs(b);
    }

    @Test
    void getBrand_notFound_throwsBrandNotExistsException() {
        when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrand(BRAND_ID))
                .isInstanceOf(BrandNotExistsException.class);
    }

    // ─── getBrandByName ───────────────────────────────────────────────────────

    @Test
    void getBrandByName_exists_returnsBrand() {
        Brand b = brand("Adidas");
        when(brandRepository.findOneByName("Adidas")).thenReturn(Optional.of(b));

        assertThat(brandService.getBrandByName("Adidas")).isSameAs(b);
    }

    @Test
    void getBrandByName_notFound_throwsBrandNotExistsException() {
        when(brandRepository.findOneByName("Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrandByName("Unknown"))
                .isInstanceOf(BrandNotExistsException.class);
    }

    // ─── getBrandList ──────────────────────────────────────────────────────────

    @Test
    void getBrandList_nonEmpty_returnsList() {
        List<Brand> brands = List.of(brand("Nike"), brand("Adidas"));
        when(brandRepository.findAll()).thenReturn(brands);

        assertThat(brandService.getBrandList()).hasSize(2);
    }

    @Test
    void getBrandList_empty_throwsBrandListEmptyException() {
        when(brandRepository.findAll()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> brandService.getBrandList())
                .isInstanceOf(BrandListEmptyException.class);
    }

    // ─── addBrand ─────────────────────────────────────────────────────────────

    @Test
    void addBrand_newName_savesAndReturnsBrand() {
        when(brandRepository.findOneByName("Puma")).thenReturn(Optional.empty());
        Brand saved = brand("Puma");
        when(brandRepository.save(any(Brand.class))).thenReturn(saved);

        Brand result = brandService.addBrand("Puma");

        verify(brandRepository).save(any(Brand.class));
        assertThat(result.getName()).isEqualTo("Puma");
    }

    @Test
    void addBrand_duplicateName_throwsBrandAlreadyExistsException() {
        when(brandRepository.findOneByName("Nike")).thenReturn(Optional.of(brand("Nike")));

        assertThatThrownBy(() -> brandService.addBrand("Nike"))
                .isInstanceOf(BrandAlreadyExistsException.class);

        verify(brandRepository, never()).save(any());
    }
}
