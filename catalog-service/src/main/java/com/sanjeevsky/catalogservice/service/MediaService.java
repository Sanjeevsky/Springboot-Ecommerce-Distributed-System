package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.model.dto.ImageUploadRequest;

public interface MediaService {

    /** Store an uploaded product image and return its public URL. */
    String uploadProductImage(ImageUploadRequest request);
}
