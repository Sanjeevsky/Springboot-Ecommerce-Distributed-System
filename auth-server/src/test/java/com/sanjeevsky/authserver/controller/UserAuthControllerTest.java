package com.sanjeevsky.authserver.controller;

import com.sanjeevsky.authserver.exceptions.GlobalExceptionHandler;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserAuthControllerTest {

    private static final String EMAIL = "alice@example.com";

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserAuthController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signup_validRequest_returns201AndForwardsUser() throws Exception {
        when(userService.registerUser(any(User.class))).thenReturn(user());

        mockMvc.perform(post("/auth-service/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).registerUser(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getPassword()).isEqualTo("secret123");
    }

    @Test
    void login_validRequest_returnsToken() throws Exception {
        when(userService.authenticateUser(any(UserDTO.class))).thenReturn(new LoginDTO(EMAIL, "token-1"));

        mockMvc.perform(post("/auth-service/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.token").value("token-1"));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(userService).authenticateUser(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getPassword()).isEqualTo("secret123");
    }

    @Test
    void login_invalidEmail_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(post("/auth-service/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid email format")));

        verifyNoInteractions(userService);
    }

    @Test
    void updatePassword_validRequest_forwardsRequest() throws Exception {
        when(userService.updatePassword(any(UpdatePasswordRequest.class))).thenReturn("Password updated successfully");

        mockMvc.perform(put("/auth-service/updatePassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL
                                + "\",\"oldPassword\":\"secret123\",\"newPassword\":\"secret456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Password updated successfully"));

        ArgumentCaptor<UpdatePasswordRequest> captor = ArgumentCaptor.forClass(UpdatePasswordRequest.class);
        verify(userService).updatePassword(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getOldPassword()).isEqualTo("secret123");
        assertThat(captor.getValue().getNewPassword()).isEqualTo("secret456");
    }

    @Test
    void updatePassword_shortNewPassword_returns400BeforeServiceCall() throws Exception {
        mockMvc.perform(put("/auth-service/updatePassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL
                                + "\",\"oldPassword\":\"secret123\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "New password must be at least 6 characters")));

        verifyNoInteractions(userService);
    }

    private User user() {
        return new User(EMAIL, "secret123", null, null);
    }
}
