package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.exceptions.ProductNotExistsException;
import com.sanjeevsky.catalogservice.exceptions.VariantNotExistsException;
import com.sanjeevsky.catalogservice.model.Variant;
import com.sanjeevsky.catalogservice.model.dto.VariantDTO;
import com.sanjeevsky.catalogservice.service.VariantService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/catalog-service/variant/")
public class VariantController {
    @Autowired
    VariantService service;
    @Autowired
    ModelMapper mapper;

    @PostMapping("/add/{productId}")
    public ResponseEntity<?> addVariant(@PathVariable("productId") UUID productId, @RequestBody VariantDTO variantDTO) throws ProductNotExistsException {
        Variant variant = mapper.map(variantDTO, Variant.class);
        return new ResponseEntity<>(service.addVariant(productId, variant), HttpStatus.CREATED);
    }

    @GetMapping("/{variantId}")
    public ResponseEntity<?> getVariant(@PathVariable("variantId") UUID variantId) throws VariantNotExistsException {
        return new ResponseEntity<>(service.getVariant(variantId), HttpStatus.OK);
    }
}
