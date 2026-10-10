package com.docuvault.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docuvault.shared.validation.PasswordRules;
import com.docuvault.support.IntegrationTest;
import com.docuvault.user.Role;
import com.docuvault.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the privilege-escalation fix.
 *
 * <p>The original accepted a {@code role} from the registration body and saved
 * it verbatim, so anyone could create themselves an administrator account
 * against the public endpoint. These tests fail if that ever comes back.
 */
@AutoConfigureMockMvc
class RegistrationSecurityTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("a role supplied in the registration body is ignored")
    void rejectsSelfAssignedRole() throws Exception {
        // First account is the bootstrap admin, so register one to get past that.
        register("{\"name\":\"Owner\",\"email\":\"owner@example.com\",\"password\":\"correct-horse-battery\"}");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mallory",
                                  "email": "mallory@example.com",
                                  "password": "correct-horse-battery",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        assertThat(userRepository.findByEmailIgnoreCase("mallory@example.com"))
                .get()
                .extracting(user -> user.getRole())
                .isEqualTo(Role.EMPLOYEE);
    }

    @Test
    @DisplayName("the first account to register becomes the administrator")
    void firstUserBecomesAdmin() throws Exception {
        register("{\"name\":\"Owner\",\"email\":\"first@example.com\",\"password\":\"correct-horse-battery\"}")
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("every account after the first is an employee")
    void laterUsersAreEmployees() throws Exception {
        register("{\"name\":\"Owner\",\"email\":\"first@example.com\",\"password\":\"correct-horse-battery\"}");
        register("{\"name\":\"Second\",\"email\":\"second@example.com\",\"password\":\"correct-horse-battery\"}")
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("a duplicate email is a conflict, not a second account")
    void rejectsDuplicateEmail() throws Exception {
        register("{\"name\":\"Owner\",\"email\":\"dupe@example.com\",\"password\":\"correct-horse-battery\"}");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"email\":\"DUPE@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a password below the minimum length is rejected with a field error")
    void rejectsTooShortPassword() throws Exception {
        // One character under the limit, so the test pins the actual boundary
        // rather than passing for any password that happens to be short.
        String tooShort = "x".repeat(PasswordRules.MIN_LENGTH - 1);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Weak\",\"email\":\"weak@example.com\",\"password\":\"" + tooShort + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("a password of exactly the minimum length is accepted")
    void acceptsMinimumLengthPassword() throws Exception {
        String exactly = "x".repeat(PasswordRules.MIN_LENGTH);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Boundary\",\"email\":\"boundary@example.com\",\"password\":\"" + exactly + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a password past BCrypt's 72-byte ceiling is rejected rather than silently truncated")
    void rejectsOverlongPassword() throws Exception {
        String tooLong = "x".repeat(PasswordRules.MAX_LENGTH + 1);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long\",\"email\":\"long@example.com\",\"password\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("wrong credentials produce 401, not 500")
    void wrongPasswordIsUnauthorized() throws Exception {
        register("{\"name\":\"Owner\",\"email\":\"login@example.com\",\"password\":\"correct-horse-battery\"}");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"wrong-password-here\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown email fails exactly like a wrong password")
    void unknownEmailIsIndistinguishable() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
