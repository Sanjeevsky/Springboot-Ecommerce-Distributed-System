package com.sanjeevsky.catalogservice.service;

import com.sanjeevsky.catalogservice.exceptions.InvalidProductRequestException;
import com.sanjeevsky.catalogservice.model.dto.ImageUploadRequest;
import com.sanjeevsky.catalogservice.service.impl.MediaServiceImpl;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final String BASE_URL = "http://localhost:9000/product-images";

    @Mock
    private MinioClient minioClient;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(minioClient, "product-images", BASE_URL + "/");
    }

    private static String pngBase64() {
        return Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5});
    }

    @Test
    void upload_validPng_storesAndReturnsPublicUrl() throws Exception {
        String url = mediaService.uploadProductImage(
                new ImageUploadRequest("logo.png", "image/png", pngBase64()));

        assertThat(url).startsWith(BASE_URL + "/products/");
        assertThat(url).endsWith(".png");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("product-images");
        assertThat(captor.getValue().object()).startsWith("products/");
    }

    @Test
    void upload_acceptsDataUrlPrefix() throws Exception {
        String url = mediaService.uploadProductImage(
                new ImageUploadRequest("p.jpg", "image/jpeg", "data:image/jpeg;base64," + pngBase64()));

        assertThat(url).endsWith(".jpg");
        verify(minioClient).putObject(org.mockito.ArgumentMatchers.any(PutObjectArgs.class));
    }

    @Test
    void upload_unsupportedType_throwsAndSkipsStorage() {
        assertThatThrownBy(() -> mediaService.uploadProductImage(
                new ImageUploadRequest("x.svg", "image/svg+xml", pngBase64())))
                .isInstanceOf(InvalidProductRequestException.class);
        verifyNoInteractions(minioClient);
    }

    @Test
    void upload_missingData_throws() {
        assertThatThrownBy(() -> mediaService.uploadProductImage(
                new ImageUploadRequest("x.png", "image/png", "  ")))
                .isInstanceOf(InvalidProductRequestException.class);
        verifyNoInteractions(minioClient);
    }

    @Test
    void upload_invalidBase64_throws() {
        assertThatThrownBy(() -> mediaService.uploadProductImage(
                new ImageUploadRequest("x.png", "image/png", "not!valid!base64")))
                .isInstanceOf(InvalidProductRequestException.class);
        verifyNoInteractions(minioClient);
    }
}
