package com.opspilot.auth;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.opspilot.common.error.GlobalExceptionHandler;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationTest {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);

    @Test
    void registersUserWithNormalizedEmailAndHashedPassword() throws Exception {
        UserStore store = new UserStore();
        MockMvc mockMvc = mockMvc(store);

        mockMvc.perform(register("Joan@Example.COM", "password123", " Joan "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("joan@example.com"))
                .andExpect(jsonPath("$.name").value("Joan"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertNotEquals("password123", store.savedUser.getPasswordHash());
        assertTrue(PASSWORD_ENCODER.matches("password123", store.savedUser.getPasswordHash()));
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc(new UserStore()).perform(register("not-an-email", "password123", "Joan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc(new UserStore()).perform(register("joan@example.com", "short", "Joan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc(new UserStore()).perform(register("joan@example.com", "password123", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        UserStore store = new UserStore();
        MockMvc mockMvc = mockMvc(store);
        mockMvc.perform(register("joan@example.com", "password123", "Joan"))
                .andExpect(status().isCreated());
        mockMvc.perform(register("JOAN@example.com", "anotherpassword", "Joan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    private MockMvc mockMvc(UserStore store) {
        AuthService service = new AuthService(store.repository(), PASSWORD_ENCODER);
        return MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String email, String password, String name) {
        return post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"%s\"}"
                        .formatted(email, password, name));
    }

    private static final class UserStore implements InvocationHandler {
        private final Map<String, User> usersByEmail = new HashMap<>();
        private long nextId = 1;
        private User savedUser;

        UserRepository repository() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "existsByEmail" -> usersByEmail.containsKey(args[0]);
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
            savedUser = user;
            return user;
        }
    }
}
