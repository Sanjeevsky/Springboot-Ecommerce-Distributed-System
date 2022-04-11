package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Variant;

import java.util.UUID;

public interface VariantService {
    Variant addVariant(UUID productId, Variant variant) throws ProductNotExistsException;

    Variant getVariant(UUID variantId) throws VariantNotExistsException;
}
