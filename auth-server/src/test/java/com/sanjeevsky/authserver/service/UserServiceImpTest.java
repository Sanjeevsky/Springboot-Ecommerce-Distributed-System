package com.sanjeevsky.authserver.service;

import com.sanjeevsky.authserver.exceptions.CredentialsMismatchException;
import com.sanjeevsky.authserver.exceptions.InvalidAuthRequestException;
import com.sanjeevsky.authserver.exceptions.NoSuchUserExistsException;
import com.sanjeevsky.authserver.exceptions.UserAlreadyExistsException;
import com.sanjeevsky.authserver.jwtgenerator.JwtTokenGenerator;
import com.sanjeevsky.authserver.modal.LoginDTO;
import com.sanjeevsky.authserver.modal.UpdatePasswordRequest;
import com.sanjeevsky.authserver.modal.User;
import com.sanjeevsky.authserver.modal.UserDTO;
import com.sanjeevsky.authserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImpTest {

    @Mock private UserRepository repository;
    @Mock private JwtTokenGenerator generator;
    @Mock private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImp userService;

    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "Secret123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";

    private User storedUser() {
        User u = new User();
        u.setEmail(EMAIL);
        u.setPassword(ENCODED_PASSWORD);
        return u;
    }

    // ─── registerUser ──────────────────────────────────────────────────────────

    @Test
    void registerUser_newUser_encodesPasswordAndSaves() {
        User incoming = new User();
        incoming.setEmail(EMAIL);
        incoming.setPassword(RAW_PASSWORD);

        when(repository.findById(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(incoming);

        assertThat(result.getPassword()).isEqualTo(ENCODED_PASSWORD);
        verify(passwordEncoder).encode(RAW_PASSWORD);
        verify(repository).save(incoming);
    }

    @Test
    void registerUser_existingEmail_throwsUserAlreadyExistsException() {
        when(repository.findById(EMAIL)).thenReturn(Optional.of(storedUser()));

        User incoming = new User();
        incoming.setEmail(EMAIL);
        incoming.setPassword(RAW_PASSWORD);

        assertThatThrownBy(() -> userService.registerUser(incoming))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(repository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerUser_trimsEmailBeforeLookupAndSave() {
        User incoming = new User();
        incoming.setEmail("  " + EMAIL + "  ");
        incoming.setPassword(RAW_PASSWORD);

        when(repository.findById(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(incoming);

        assertThat(result.getEmail()).isEqualTo(EMAIL);
        verify(repository).findById(EMAIL);
        verify(repository).save(incoming);
    }

    @Test
    void registerUser_nullRequest_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.registerUser(null))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("User request is required");

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void registerUser_blankEmail_throwsInvalidAuthRequestException() {
        User incoming = new User();
        incoming.setEmail("   ");
        incoming.setPassword(RAW_PASSWORD);

        assertThatThrownBy(() -> userService.registerUser(incoming))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Email is required");

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void registerUser_shortPassword_throwsInvalidAuthRequestException() {
        User incoming = new User();
        incoming.setEmail(EMAIL);
        incoming.setPassword("short");

        assertThatThrownBy(() -> userService.registerUser(incoming))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Password must be at least 6 characters");

        verifyNoInteractions(repository, passwordEncoder);
    }

    // ─── authenticateUser ──────────────────────────────────────────────────────

    @Test
    void authenticateUser_correctCredentials_returnsLoginDTO() {
        when(repository.findById(EMAIL)).thenReturn(Optional.of(storedUser()));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        LoginDTO expectedToken = new LoginDTO(EMAIL, "jwt.token.here");
        UserDTO dto = new UserDTO(EMAIL, RAW_PASSWORD);
        when(generator.generateToken(dto)).thenReturn(expectedToken);

        LoginDTO result = userService.authenticateUser(dto);

        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getToken()).isEqualTo("jwt.token.here");
    }

    @Test
    void authenticateUser_wrongPassword_throwsCredentialsMismatchException() {
        when(repository.findById(EMAIL)).thenReturn(Optional.of(storedUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.authenticateUser(new UserDTO(EMAIL, "wrongPass")))
                .isInstanceOf(CredentialsMismatchException.class);

        verify(generator, never()).generateToken(any());
    }

    @Test
    void authenticateUser_userNotFound_throwsNoSuchUserExistsException() {
        when(repository.findById(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticateUser(new UserDTO(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(NoSuchUserExistsException.class);
    }

    @Test
    void authenticateUser_trimsEmailBeforeLookupAndTokenGeneration() {
        when(repository.findById(EMAIL)).thenReturn(Optional.of(storedUser()));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        LoginDTO expectedToken = new LoginDTO(EMAIL, "jwt.token.here");
        UserDTO dto = new UserDTO("  " + EMAIL + "  ", RAW_PASSWORD);
        when(generator.generateToken(dto)).thenReturn(expectedToken);

        LoginDTO result = userService.authenticateUser(dto);

        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(dto.getEmail()).isEqualTo(EMAIL);
        verify(repository).findById(EMAIL);
        verify(generator).generateToken(dto);
    }

    @Test
    void authenticateUser_nullRequest_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.authenticateUser(null))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Login request is required");

        verifyNoInteractions(repository, passwordEncoder, generator);
    }

    @Test
    void authenticateUser_invalidEmail_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.authenticateUser(new UserDTO("not-an-email", RAW_PASSWORD)))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Invalid email format");

        verifyNoInteractions(repository, passwordEncoder, generator);
    }

    @Test
    void authenticateUser_shortPassword_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.authenticateUser(new UserDTO(EMAIL, "short")))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Password must be at least 6 characters");

        verifyNoInteractions(repository, passwordEncoder, generator);
    }

    // ─── updatePassword ────────────────────────────────────────────────────────

    @Test
    void updatePassword_success_encodesNewPasswordAndSaves() {
        User user = storedUser();
        when(repository.findById(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        String newEncoded = "$2a$10$newEncodedHash";
        when(passwordEncoder.encode("NewPass456")).thenReturn(newEncoded);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String message = userService.updatePassword(
                new UpdatePasswordRequest(EMAIL, RAW_PASSWORD, "NewPass456"));

        assertThat(message).isEqualTo("Password updated successfully");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo(newEncoded);
    }

    @Test
    void updatePassword_wrongOldPassword_throwsCredentialsMismatchException() {
        when(repository.findById(EMAIL)).thenReturn(Optional.of(storedUser()));
        when(passwordEncoder.matches("wrongOld", ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(
                new UpdatePasswordRequest(EMAIL, "wrongOld", "NewPass456")))
                .isInstanceOf(CredentialsMismatchException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void updatePassword_userNotFound_throwsNoSuchUserExistsException() {
        when(repository.findById(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updatePassword(
                new UpdatePasswordRequest(EMAIL, RAW_PASSWORD, "NewPass456")))
                .isInstanceOf(NoSuchUserExistsException.class);
    }

    @Test
    void updatePassword_trimsEmailBeforeLookupAndSave() {
        User user = storedUser();
        when(repository.findById(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("$2a$10$newEncodedHash");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String message = userService.updatePassword(
                new UpdatePasswordRequest("  " + EMAIL + "  ", RAW_PASSWORD, "NewPass456"));

        assertThat(message).isEqualTo("Password updated successfully");
        verify(repository).findById(EMAIL);
    }

    @Test
    void updatePassword_nullRequest_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.updatePassword(null))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Update password request is required");

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void updatePassword_blankOldPassword_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.updatePassword(
                new UpdatePasswordRequest(EMAIL, "   ", "NewPass456")))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("Old password is required");

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void updatePassword_shortNewPassword_throwsInvalidAuthRequestException() {
        assertThatThrownBy(() -> userService.updatePassword(
                new UpdatePasswordRequest(EMAIL, RAW_PASSWORD, "short")))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessage("New password must be at least 6 characters");

        verifyNoInteractions(repository, passwordEncoder);
    }
}
