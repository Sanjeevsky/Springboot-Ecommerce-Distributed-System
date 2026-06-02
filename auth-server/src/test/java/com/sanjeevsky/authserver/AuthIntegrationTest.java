package com.sanjeevsky.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.boot.admin.client.enabled=false",
                "spring.zipkin.enabled=false",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=",
                "jwt.secret=test-jwt-secret",
                "spring.datasource.url=jdbc:h2:mem:auth-integration-db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // ─── Signup ───────────────────────────────────────────────────────────────

    @Test
    void signup_validUser_returns201WithEmail() throws Exception {
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        String body = "{\"email\":\"dup@example.com\",\"password\":\"secret123\"}";
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth-service/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("bob@example.com"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"password\":\"rightpass\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth-service/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUser_returns404() throws Exception {
        mockMvc.perform(post("/auth-service/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── UpdatePassword ───────────────────────────────────────────────────────

    @Test
    void updatePassword_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dave@example.com\",\"password\":\"oldpass1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/auth-service/updatePassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dave@example.com\",\"oldPassword\":\"oldpass1\",\"newPassword\":\"newpass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Password updated successfully"));
    }

    @Test
    void updatePassword_wrongOldPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eve@example.com\",\"password\":\"correctold\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/auth-service/updatePassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eve@example.com\",\"oldPassword\":\"wrongold\",\"newPassword\":\"newpass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePassword_unknownUser_returns404() throws Exception {
        mockMvc.perform(put("/auth-service/updatePassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"oldPassword\":\"x\",\"newPassword\":\"newpass1\"}"))
                .andExpect(status().isNotFound());
    }
}
