package com.sanjeevsky.shoppingcartservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignDecoderConfig {

    @Bean
    public Decoder feignDecoder(ObjectFactory<HttpMessageConverters> messageConverters, ObjectMapper objectMapper) {
        return new ApiResponseFeignDecoder(new SpringDecoder(messageConverters), objectMapper);
    }
}
