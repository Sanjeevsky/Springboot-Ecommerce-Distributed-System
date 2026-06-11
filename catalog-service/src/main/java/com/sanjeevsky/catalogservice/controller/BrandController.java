package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.service.BrandService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.utils.LoggingConstants.*;

@RestController
@Slf4j
@RequestMapping("/catalog-service")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping("/getBrand/{id}")
    public ResponseEntity<ApiResponse<Brand>> getBrand(@PathVariable UUID id) {
        log.info(GET_BRAND_REQUEST_WITH_BRAND_ID, id);
        return ResponseEntity.ok(ApiResponse.ok(brandService.getBrand(id)));
    }

    @GetMapping("/getBrandByName/{name}")
    public ResponseEntity<ApiResponse<Brand>> getBrandByName(@PathVariable String name) {
        log.info(GET_BRAND_REQUEST_WITH_BRAND_NAME, name);
        return ResponseEntity.ok(ApiResponse.ok(brandService.getBrandByName(name)));
    }

    @GetMapping("/getBrands")
    public ResponseEntity<ApiResponse<List<Brand>>> getBrands() {
        log.info(GET_ALL_BRAND_REQUEST);
        return ResponseEntity.ok(ApiResponse.ok(brandService.getBrandList()));
    }

    @AdminOnly
    @PostMapping("/add-brand")
    public ResponseEntity<ApiResponse<Brand>> addBrand(@RequestParam("name") String name) {
        log.info(ADD_BRAND_REQUEST_WITH_BRAND_NAME, name);
        return new ResponseEntity<>(ApiResponse.ok("Brand added", brandService.addBrand(name)), HttpStatus.CREATED);
    }
}
