package com.sanjeevsky.notificationservice.config;

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
        title = "Notification Service",
        version = "1.0.0",
        description = "Order and payment event notifications",
        contact = @Contact(name = "Sanjeevsky", url = "https://github.com/Sanjeevsky")
    ),
    servers = {
        @Server(url = "http://localhost:8087", description = "Direct"),
        @Server(url = "http://localhost:8081", description = "Via API Gateway")
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
