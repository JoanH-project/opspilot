package com.opspilot.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.auth.dto.RegisterRequest;
import com.opspilot.common.error.GlobalExceptionHandler;
import com.opspilot.security.AuthenticationErrorWriter;
import com.opspilot.security.JwtAuthenticationFilter;
import com.opspilot.security.RestAuthenticationEntryPoint;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import com.opspilot.user.UserController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationTest {
    private static final String SECRET = "test-jwt-secret-at-least-thirty-two-characters-long";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void loginReturnsBearerTokenForNormalizedEmailWithoutPasswordHash() throws Exception {
        UserStore store = new UserStore();
        JwtService jwtService = jwtService(3600);
        AuthService service = service(store, jwtService);
        service.register(new RegisterRequest("joan@example.com", "password123", "Joan"));

        MockMvc mockMvc = mockMvc(service);
        String response = mockMvc.perform(login("JOAN@EXAMPLE.COM", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("joan@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = new ObjectMapper().readTree(response).get("accessToken").asText();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertFalse(payload.contains("passwordHash"));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnGenericUnauthorizedResponse() throws Exception {
        UserStore store = new UserStore();
        AuthService service = service(store, jwtService(3600));
        service.register(new RegisterRequest("joan@example.com", "password123", "Joan"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(login("joan@example.com", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mockMvc.perform(login("missing@example.com", "password123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void validJwtAuthenticatesFilterAndInvalidJwtReturnsUnauthorized() throws Exception {
        UserStore store = new UserStore();
        JwtService jwtService = jwtService(3600);
        AuthService service = service(store, jwtService);
        service.register(new RegisterRequest("joan@example.com", "password123", "Joan"));
        String token = service.login(new com.opspilot.auth.dto.LoginRequest("joan@example.com", "password123")).accessToken();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, store.repository(),
                new AuthenticationErrorWriter(new ObjectMapper().findAndRegisterModules()));

        MockHttpServletRequest validRequest = new MockHttpServletRequest("GET", "/api/users/me");
        validRequest.addHeader("Authorization", "Bearer " + token);
        AtomicBoolean chainCalled = new AtomicBoolean();
        filter.doFilter(validRequest, new MockHttpServletResponse(), (request, response) -> chainCalled.set(true));
        assertTrue(chainCalled.get());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());

        SecurityContextHolder.clearContext();
        MockHttpServletRequest invalidRequest = new MockHttpServletRequest("GET", "/api/users/me");
        invalidRequest.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(invalidRequest, invalidResponse, (request, response) -> chainCalled.set(false));
        org.junit.jupiter.api.Assertions.assertEquals(401, invalidResponse.getStatus());
    }

    @Test
    void currentUserPathWithoutTokenReturnsUnauthorized() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(
                new AuthenticationErrorWriter(new ObjectMapper().findAndRegisterModules()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new InsufficientAuthenticationException("Missing token"));
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    void currentUserReturnsOnlySafeUserDataForAuthenticatedUser() {
        UserStore store = new UserStore();
        AuthService service = service(store, jwtService(3600));
        service.register(new RegisterRequest("joan@example.com", "password123", "Joan"));
        User user = store.usersByEmail.get("joan@example.com");

        var response = new UserController(store.repository())
                .currentUser(new UsernamePasswordAuthenticationToken(user.getId(), null));
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals("joan@example.com", response.getBody().email());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        UserStore store = new UserStore();
        JwtService expiredJwtService = jwtService(1);
        AuthService service = service(store, expiredJwtService);
        service.register(new RegisterRequest("joan@example.com", "password123", "Joan"));
        String token = service.login(new com.opspilot.auth.dto.LoginRequest("joan@example.com", "password123")).accessToken();
        Thread.sleep(1100);
        assertFalse(expiredJwtService.validateToken(token));
    }

    private AuthService service(UserStore store, JwtService jwtService) {
        return new AuthService(store.repository(), passwordEncoder, jwtService);
    }

    private MockMvc mockMvc(AuthService service) {
        return MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private JwtService jwtService(long expirationSeconds) { return new JwtService(SECRET, expirationSeconds); }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static final class UserStore implements InvocationHandler {
        private final Map<String, User> usersByEmail = new HashMap<>();
        private final Map<Long, User> usersById = new HashMap<>();
        private long nextId = 1;
        UserRepository repository() {
            return (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class}, this);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "existsByEmail" -> usersByEmail.containsKey(args[0]);
                case "findByEmail" -> Optional.ofNullable(usersByEmail.get(args[0]));
                case "findById" -> Optional.ofNullable(usersById.get(args[0]));
                case "saveAndFlush" -> save((User) args[0]);
                case "toString" -> "UserStore";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
        private User save(User user) throws ReflectiveOperationException {
            Method timestamps = User.class.getDeclaredMethod("setCreationTimestamps");
            timestamps.setAccessible(true);
            timestamps.invoke(user);
            Field id = User.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(user, nextId++);
            usersByEmail.put(user.getEmail(), user);
            usersById.put(user.getId(), user);
            return user;
        }
    }
}
