package com.classroom.core.controller;

import com.classroom.core.TestcontainersConfig;
import com.classroom.core.dto.auth.AuthResponse;
import com.classroom.core.dto.auth.LoginRequest;
import com.classroom.core.dto.auth.RegisterRequest;
import com.classroom.core.dto.auth.UserDto;
import com.classroom.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private RegisterRequest registerRequest(String username, String password, String displayName) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setDisplayName(displayName);
        return req;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private String registerAndGetToken(String username, String password) {
        RegisterRequest req = registerRequest(username, password, null);
        ResponseEntity<AuthResponse> resp = restTemplate.postForEntity("/api/v1/auth/register", req, AuthResponse.class);
        return resp.getBody().getToken();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Nested
    class Register {

        @Test
        void returns201_andTokenAndUser_onValidRequest() {
            RegisterRequest request = registerRequest("johndoe", "password123", "John Doe");

            ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", request, AuthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getToken()).isNotBlank();
            assertThat(response.getBody().getUser().getUsername()).isEqualTo("johndoe");
            assertThat(response.getBody().getUser().getDisplayName()).isEqualTo("John Doe");
            assertThat(response.getBody().getUser().getId()).isNotNull();
            assertThat(response.getBody().getUser().getCreatedAt()).isNotNull();
        }

        @Test
        void persists_userInDatabase() {
            RegisterRequest request = registerRequest("johndoe", "password123", "John Doe");

            restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

            assertThat(userRepository.findByUsername("johndoe")).isPresent();
        }

        @Test
        void returns409_whenUsernameAlreadyTaken() {
            restTemplate.postForEntity("/api/v1/auth/register",
                    registerRequest("johndoe", "password123", "John"), AuthResponse.class);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register",
                    registerRequest("johndoe", "otherpass1", "Jane"),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void returns400_whenUsernameIsBlank() {
            RegisterRequest request = registerRequest("", "password123", null);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenUsernameTooShort() {
            RegisterRequest request = registerRequest("ab", "password123", null);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenPasswordIsBlank() {
            RegisterRequest request = registerRequest("johndoe", "", null);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenPasswordTooShort() {
            RegisterRequest request = registerRequest("johndoe", "12345", null);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenBodyIsMissing() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(null, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/register", entity, String.class);

            assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        }

        @Test
        void doesNotStorePasswordInPlaintext() {
            restTemplate.postForEntity("/api/v1/auth/register",
                    registerRequest("johndoe", "password123", null), AuthResponse.class);

            var user = userRepository.findByUsername("johndoe").orElseThrow();
            assertThat(user.getPasswordHash()).isNotEqualTo("password123");
            assertThat(user.getPasswordHash()).startsWith("$2");
        }

        @Test
        void returnedTokenIsUsableForAuthenticatedEndpoints() {
            ResponseEntity<AuthResponse> regResp = restTemplate.postForEntity(
                    "/api/v1/auth/register",
                    registerRequest("johndoe", "password123", "John"),
                    AuthResponse.class);

            String token = regResp.getBody().getToken();

            ResponseEntity<UserDto> meResp = restTemplate.exchange(
                    "/api/v1/auth/me", HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), UserDto.class);

            assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(meResp.getBody().getUsername()).isEqualTo("johndoe");
        }
    }

    @Nested
    class Login {

        @Test
        void returns200_andTokenAndUser_onValidCredentials() {
            restTemplate.postForEntity("/api/v1/auth/register",
                    registerRequest("johndoe", "password123", "John Doe"), AuthResponse.class);

            ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("johndoe", "password123"), AuthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getToken()).isNotBlank();
            assertThat(response.getBody().getUser().getUsername()).isEqualTo("johndoe");
        }

        @Test
        void returns401_whenPasswordIsWrong() {
            restTemplate.postForEntity("/api/v1/auth/register",
                    registerRequest("johndoe", "password123", null), AuthResponse.class);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("johndoe", "wrongpassword"), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void returns401_whenUserDoesNotExist() {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("ghost", "password123"), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void returns400_whenUsernameIsBlank() {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("", "password123"), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns400_whenPasswordIsBlank() {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("johndoe", ""), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returnedTokenIsUsableForAuthenticatedEndpoints() {
            restTemplate.postForEntity("/api/v1/auth/register",
                    registerRequest("johndoe", "password123", "John"), AuthResponse.class);

            ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                    "/api/v1/auth/login", loginRequest("johndoe", "password123"), AuthResponse.class);

            String token = loginResp.getBody().getToken();

            ResponseEntity<UserDto> meResp = restTemplate.exchange(
                    "/api/v1/auth/me", HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), UserDto.class);

            assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(meResp.getBody().getUsername()).isEqualTo("johndoe");
        }
    }

    @Nested
    class Me {

        @Test
        void returns200_withCurrentUser_whenAuthenticated() {
            String token = registerAndGetToken("johndoe", "password123");

            ResponseEntity<UserDto> response = restTemplate.exchange(
                    "/api/v1/auth/me", HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), UserDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getUsername()).isEqualTo("johndoe");
            assertThat(response.getBody().getId()).isNotNull();
        }

        @Test
        void returns401_whenNoTokenProvided() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/auth/me", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void returns401_whenTokenIsInvalid() {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/auth/me", HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders("garbage.token.here")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void returns401_whenTokenIsExpired() {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/auth/me", HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(
                            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJ0ZXN0IiwiZXhwIjoxfQ.invalid"
                    )), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
