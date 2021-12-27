package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.BrandAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;

import java.util.List;
import java.util.UUID;

public interface BrandService {
    Brand getBrand(UUID brandId) throws BrandNotExistsException;

    Brand getBrandByName(String name) throws BrandNotExistsException;

    List<Brand> getBrandList() throws BrandListEmptyException;

    Brand addBrand(String brand) throws BrandAlreadyExistsException;
}
