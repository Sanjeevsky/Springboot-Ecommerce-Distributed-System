package com.sanjeevsky.shoppingcartservice.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper getMapper() {
        return new ModelMapper();
    }
}
