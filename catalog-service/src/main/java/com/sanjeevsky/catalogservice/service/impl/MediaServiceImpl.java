package com.sanjeevsky.catalogservice.service.impl;

import com.sanjeevsky.catalogservice.exceptions.InvalidProductRequestException;
import com.sanjeevsky.catalogservice.model.dto.ImageUploadRequest;
import com.sanjeevsky.catalogservice.service.MediaService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif");

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicBaseUrl;

    public MediaServiceImpl(
            MinioClient minioClient,
            @Value("${minio.bucket:product-images}") String bucket,
            @Value("${media.public-base-url:http://localhost:9000/product-images}") String publicBaseUrl) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        // Trim a trailing slash so URL joining stays clean.
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /** Ensure the bucket exists and is anonymously readable so <img> tags can load objects. */
    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            log.info("MinIO bucket '{}' ready (public-read)", bucket);
        } catch (Exception e) {
            // Don't fail startup if object storage is briefly unavailable; uploads retry the ensure.
            log.warn("Could not initialize MinIO bucket '{}': {}", bucket, e.getMessage());
        }
    }

    @Override
    public String uploadProductImage(ImageUploadRequest request) {
        if (request == null || request.getDataBase64() == null || request.getDataBase64().isBlank()) {
            throw new InvalidProductRequestException("Image data is required");
        }
        String contentType = normalizeContentType(request.getContentType());
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new InvalidProductRequestException("Unsupported image type: " + request.getContentType());
        }

        byte[] bytes = decode(request.getDataBase64());
        if (bytes.length == 0) {
            throw new InvalidProductRequestException("Image data is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidProductRequestException("Image exceeds the 5 MB limit");
        }

        String key = "products/" + UUID.randomUUID() + "." + extension;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload product image {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to store image", e);
        }
        return publicBaseUrl + "/" + key;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase();
    }

    private byte[] decode(String data) {
        // Accept both bare base64 and "data:<type>;base64,<payload>" URLs.
        String payload = data;
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma >= 0) {
            payload = data.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload.strip());
        } catch (IllegalArgumentException e) {
            throw new InvalidProductRequestException("Image data is not valid base64");
        }
    }

    private static String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }
}
