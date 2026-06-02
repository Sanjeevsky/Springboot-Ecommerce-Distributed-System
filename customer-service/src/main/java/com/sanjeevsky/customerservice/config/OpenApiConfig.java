package com.sanjeevsky.customerservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "Customer Service",
        version = "1.0.0",
        description = "Customer addresses and order history",
        contact = @Contact(name = "Sanjeevsky", url = "https://github.com/Sanjeevsky")
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "API Gateway")
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter JWT token obtained from POST /auth-service/login"
)
@Configuration
public class OpenApiConfig {
}
