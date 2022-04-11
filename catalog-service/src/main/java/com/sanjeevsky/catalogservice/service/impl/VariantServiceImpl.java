package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.repository.VariantRepository;
import com.sanjeevsky.catalogservice.service.VariantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.PRODUCT_WITH_GIVEN_ID_DOESN_T_EXISTS;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS;

@Service
public class VariantServiceImpl implements VariantService {

    @Autowired
    private VariantRepository variantRepository;
    @Autowired
    private ProductRepository productRepository;

    @Override
    public Variant addVariant(UUID productId, Variant variant) throws ProductNotExistsException {
        Optional<Product> product = productRepository.findById(productId);
        if (product.isEmpty()) throw new ProductNotExistsException(PRODUCT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        variant.setProduct(product.get());
        return variantRepository.save(variant);
    }

    @Override
    public Variant getVariant(UUID variantId) throws VariantNotExistsException {
        Optional<Variant> variant = variantRepository.findById(variantId);
        if (variant.isEmpty()) throw new VariantNotExistsException(VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        return variant.get();
    }
}
