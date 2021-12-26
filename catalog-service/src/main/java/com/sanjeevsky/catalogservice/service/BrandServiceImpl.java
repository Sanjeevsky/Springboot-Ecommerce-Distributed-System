package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.BrandAlreadyExistsException;
import com.sanjeevsky.catalogservice.exceptions.BrandListEmptyException;
import com.sanjeevsky.catalogservice.exceptions.BrandNotExistsException;
import com.sanjeevsky.catalogservice.model.Brand;
import com.sanjeevsky.catalogservice.repository.BrandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanjeevsky.catalogservice.service.ProductServiceImpl.BRAND_DOES_NOT_EXISTS;

@Service
public class BrandServiceImpl implements BrandService {
    public static final String BRAND_LIST_EMPTY = "Brand List Empty";
    public static final String BRAND_ALREADY_EXISTS = "Brand Already Exists";
    @Autowired
    private BrandRepository brandRepository;

    @Override
    public Brand getBrand(UUID brandId) throws BrandNotExistsException {
        Optional<Brand> brand = brandRepository.findById(brandId);
        if (brand.isEmpty()) {
            throw new BrandNotExistsException(BRAND_DOES_NOT_EXISTS);
        }
        return brand.get();
    }

    @Override
    public Brand getBrandByName(String name) throws BrandNotExistsException {
        Optional<Brand> brand = brandRepository.findOneByName(name);
        if (brand.isEmpty()) {
            throw new BrandNotExistsException(BRAND_DOES_NOT_EXISTS);
        }
        return brand.get();
    }

    @Override
    public List<Brand> getBrandList() throws BrandListEmptyException {
        List<Brand> brands = brandRepository.findAll();
        if (brands.isEmpty()) {
            throw new BrandListEmptyException(BRAND_LIST_EMPTY);
        }
        return brands;
    }

    @Override
    public Brand addBrand(Brand brand) throws BrandAlreadyExistsException {
        Optional<Brand> name = brandRepository.findOneByName(brand.getName());
        if (name.isPresent()) {
            throw new BrandAlreadyExistsException(BRAND_ALREADY_EXISTS);
        }
        return brandRepository.save(brand);
    }

}
