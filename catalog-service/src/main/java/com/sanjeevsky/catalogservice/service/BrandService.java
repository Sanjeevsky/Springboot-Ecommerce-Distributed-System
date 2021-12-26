package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;

import java.util.List;

public interface BrandService {
    Brand getBrand(Long brandId) throws BrandNotExistsException;

    List<Brand> getBrandList() throws BrandListEmptyException;

    Brand addBrand(Brand brand);
}
