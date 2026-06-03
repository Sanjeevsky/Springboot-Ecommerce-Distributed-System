package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.InvalidVariantRequestException;
import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.repository.VariantRepository;
import com.sanjeevsky.catalogservice.service.VariantService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.ErrorConstants.PRODUCT_WITH_GIVEN_ID_DOESN_T_EXISTS;
import static com.sanjeevsky.catalogservice.utils.ErrorConstants.VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS;

@Service
public class VariantServiceImpl implements VariantService {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;

    public VariantServiceImpl(VariantRepository variantRepository, ProductRepository productRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Variant addVariant(UUID productId, Variant variant){
        validateVariantRequest(variant);
        Optional<Product> product = productRepository.findById(productId);
        if (product.isEmpty()) throw new ProductNotExistsException(PRODUCT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        variant.setProduct(product.get());
        return variantRepository.save(variant);
    }

    @Override
    public Variant getVariant(UUID variantId){
        Optional<Variant> variant = variantRepository.findById(variantId);
        if (variant.isEmpty()) throw new VariantNotExistsException(VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        return variant.get();
    }

    private void validateVariantRequest(Variant variant) {
        if (variant == null) {
            throw new InvalidVariantRequestException("Variant request is required");
        }
        if (isBlank(variant.getCondition1Name())) {
            throw new InvalidVariantRequestException("Primary condition name is required");
        }
        if (isBlank(variant.getCondition1Value())) {
            throw new InvalidVariantRequestException("Primary condition value is required");
        }
        if (variant.getMrpPrice() <= 0) {
            throw new InvalidVariantRequestException("MRP price must be positive");
        }
        if (variant.getSalePrice() <= 0) {
            throw new InvalidVariantRequestException("Sale price must be positive");
        }
        if (variant.getSalePrice() > variant.getMrpPrice()) {
            throw new InvalidVariantRequestException("Sale price cannot exceed MRP price");
        }

        variant.setCondition1Name(variant.getCondition1Name().trim());
        variant.setCondition1Value(variant.getCondition1Value().trim());
        if (variant.getCondition2Name() != null) {
            variant.setCondition2Name(variant.getCondition2Name().trim());
        }
        if (variant.getCondition2Value() != null) {
            variant.setCondition2Value(variant.getCondition2Value().trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
