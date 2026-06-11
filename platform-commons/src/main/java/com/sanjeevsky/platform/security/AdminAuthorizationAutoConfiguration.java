package com.sanjeevsky.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebMvcConfigurer.class)
public class AdminAuthorizationAutoConfiguration implements WebMvcConfigurer {

    @Bean
    AdminAuthorizationInterceptor adminAuthorizationInterceptor() {
        return new AdminAuthorizationInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthorizationInterceptor());
    }
}
