package com.sanjeevsky.apigateway.config;

import com.sanjeevsky.apigateway.filter.JwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.GenericFilterBean;

@Configuration
public class FilterConfig {
    public static final String API_URL = "/api/v1/*";
    @Bean
    public FilterRegistrationBean<GenericFilterBean> jwtFilter() {
        FilterRegistrationBean<GenericFilterBean> filter = new FilterRegistrationBean();
        filter.setFilter(new JwtFilter());
        filter.addUrlPatterns(API_URL);
        return filter;
    }
}