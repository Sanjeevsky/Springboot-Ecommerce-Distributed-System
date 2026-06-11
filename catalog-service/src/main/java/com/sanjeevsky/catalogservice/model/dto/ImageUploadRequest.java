package com.sanjeevsky.catalogservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base64 image upload payload. Base64 (rather than multipart) keeps the upload
 * on the existing JSON API client and makes the Postman/newman flow trivial.
 * {@code dataBase64} may be a bare base64 string or a {@code data:} URL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadRequest {
    private String filename;
    private String contentType;
    private String dataBase64;
}
