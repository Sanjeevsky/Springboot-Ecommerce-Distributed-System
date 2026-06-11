package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.InvalidVariantRequestException;
import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Product;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.model.dto.VariantUpdateRequest;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.repository.VariantRepository;
import com.sanjeevsky.catalogservice.service.VariantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Variant addVariant(UUID productId, Variant variant){
        validateProductId(productId);
        validateVariantRequest(variant);
        Optional<Product> product = productRepository.findById(productId);
        if (product.isEmpty()) throw new ProductNotExistsException(PRODUCT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        Product storedProduct = product.get();
        variant.setProduct(storedProduct);
        Variant saved = variantRepository.save(variant);
        if (!storedProduct.isHasVariant()) {
            storedProduct.setHasVariant(true);
            productRepository.save(storedProduct);
        }
        return saved;
    }

    @Override
    public Variant getVariant(UUID variantId){
        if (variantId == null) {
            throw new InvalidVariantRequestException("Variant id is required");
        }
        Optional<Variant> variant = variantRepository.findById(variantId);
        if (variant.isEmpty()) throw new VariantNotExistsException(VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS);
        return variant.get();
    }

    @Override
    public Variant updateVariant(UUID variantId, VariantUpdateRequest request) {
        if (variantId == null) {
            throw new InvalidVariantRequestException("Variant id is required");
        }
        if (request == null) {
            throw new InvalidVariantRequestException("Variant update request is required");
        }
        Variant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new VariantNotExistsException(VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS));

        if (request.getCondition1Name() != null) variant.setCondition1Name(request.getCondition1Name());
        if (request.getCondition1Value() != null) variant.setCondition1Value(request.getCondition1Value());
        if (request.getCondition2Name() != null) variant.setCondition2Name(request.getCondition2Name());
        if (request.getCondition2Value() != null) variant.setCondition2Value(request.getCondition2Value());
        if (request.getMrpPrice() != null) variant.setMrpPrice(request.getMrpPrice());
        if (request.getSalePrice() != null) variant.setSalePrice(request.getSalePrice());

        validateVariantRequest(variant);
        return variantRepository.save(variant);
    }

    @Override
    @Transactional
    public void deleteVariant(UUID variantId) {
        if (variantId == null) {
            throw new InvalidVariantRequestException("Variant id is required");
        }
        Variant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new VariantNotExistsException(VARIANT_WITH_GIVEN_ID_DOESN_T_EXISTS));
        Product product = variant.getProduct();
        variantRepository.delete(variant);
        variantRepository.flush();
        if (product != null && variantRepository.countByProductId(product.getId()) == 0) {
            product.setHasVariant(false);
        }
    }

    private void validateProductId(UUID productId) {
        if (productId == null) {
            throw new InvalidVariantRequestException("Product id is required");
        }
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
