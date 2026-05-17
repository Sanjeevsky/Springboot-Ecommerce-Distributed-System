package com.sanjeevsky.customerservice.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

public class ApiResponseFeignDecoder implements Decoder {

    private final Decoder delegate;
    private final ObjectMapper objectMapper;

    public ApiResponseFeignDecoder(Decoder delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException {
        if (response.body() == null || type == Void.class || type == void.class) {
            return null;
        }

        byte[] bodyBytes;
        try (InputStream is = response.body().asInputStream()) {
            bodyBytes = is.readAllBytes();
        }

        try {
            JsonNode root = objectMapper.readTree(bodyBytes);
            if (root.isObject() && root.has("success") && root.has("data")) {
                JsonNode dataNode = root.get("data");
                if (dataNode == null || dataNode.isNull()) {
                    return null;
                }
                return objectMapper.convertValue(dataNode, objectMapper.constructType(type));
            }
        } catch (Exception ignored) {
        }

        return delegate.decode(response.toBuilder().body(bodyBytes).build(), type);
    }
}
