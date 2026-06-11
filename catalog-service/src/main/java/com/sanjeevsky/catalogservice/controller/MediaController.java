package com.sanjeevsky.catalogservice.controller;

import com.sanjeevsky.catalogservice.model.dto.ImageUploadRequest;
import com.sanjeevsky.catalogservice.service.MediaService;
import com.sanjeevsky.platform.response.ApiResponse;
import com.sanjeevsky.platform.security.AdminOnly;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/catalog-service/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @AdminOnly
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(@RequestBody ImageUploadRequest request) {
        String url = mediaService.uploadProductImage(request);
        return new ResponseEntity<>(ApiResponse.ok("Image uploaded", Map.of("url", url)), HttpStatus.CREATED);
    }
}
