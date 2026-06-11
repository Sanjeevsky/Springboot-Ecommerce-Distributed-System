package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.model.dto.VariantUpdateRequest;

import java.util.UUID;

public interface VariantService {
    Variant addVariant(UUID productId, Variant variant) throws ProductNotExistsException;

    Variant getVariant(UUID variantId) throws VariantNotExistsException;

    Variant updateVariant(UUID variantId, VariantUpdateRequest request);

    void deleteVariant(UUID variantId);
}
