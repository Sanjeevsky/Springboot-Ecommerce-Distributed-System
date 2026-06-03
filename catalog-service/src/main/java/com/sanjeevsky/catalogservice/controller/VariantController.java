package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.model.dto.VariantDTO;
import com.sanjeevsky.catalogservice.service.VariantService;
import com.sanjeevsky.platform.response.ApiResponse;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/catalog-service/variant")
public class VariantController {

    private final VariantService service;
    private final ModelMapper mapper;

    public VariantController(VariantService service, ModelMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/add/{productId}")
    public ResponseEntity<ApiResponse<Variant>> addVariant(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody VariantDTO variantDTO) {
        Variant variant = mapper.map(variantDTO, Variant.class);
        return new ResponseEntity<>(ApiResponse.ok("Variant added", service.addVariant(productId, variant)), HttpStatus.CREATED);
    }

    @GetMapping("/{variantId}")
    public ResponseEntity<ApiResponse<Variant>> getVariant(@PathVariable("variantId") UUID variantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getVariant(variantId)));
    }
}
