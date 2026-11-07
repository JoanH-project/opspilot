package com.opspilot.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.opspilot.auth.ratelimit.AuthAction;
import com.opspilot.auth.ratelimit.AuthRateLimitProperties;
import com.opspilot.auth.ratelimit.ClientAddressResolver;
import com.opspilot.auth.ratelimit.RedisAuthRateLimiter;
import com.opspilot.auth.ratelimit.TooManyAuthenticationAttemptsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=127.0.0.1",
        "app.metrics.username=v2-integration-metrics",
        "app.metrics.password=v2-integration-metrics-password",
        "app.auth.rate-limit.enabled=true",
        "app.auth.rate-limit.ip.max-attempts=1000",
        "app.auth.rate-limit.ip.window=30s",
        "app.auth.rate-limit.account.max-attempts=3",
        "app.auth.rate-limit.account.window=3s"
})
class V2ProductionBackendIT {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("opspilot")
            .withUsername("opspilot_test")
            .withPassword("test-password");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", mysql::getJdbcUrl);
        properties.add("spring.datasource.username", mysql::getUsername);
        properties.add("spring.datasource.password", mysql::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired TestRestTemplate client;
    @LocalServerPort int apiPort;
    @LocalManagementPort int managementPort;

    @Test
    void v1ApiMysqlFlywayRedisLimitAndWorkspaceIsolationWorkEndToEnd() {
        ResponseEntity<String> health = client.getForEntity("/api/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isEqualTo("{\"status\":\"UP\"}");

        ResponseEntity<String> register = jsonPost("/api/auth/register",
                "{\"email\":\"owner-v2@example.com\",\"password\":\"password123\",\"name\":\"Owner\"}");
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).doesNotContain("passwordHash");
        assertThat(jsonPost("/api/auth/register",
                "{\"email\":\"OWNER-v2@example.com\",\"password\":\"password123\",\"name\":\"Owner\"}")
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String token = login("owner-v2@example.com", "password123", HttpStatus.OK).getBody()
                .replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        HttpHeaders auth = bearer(token);
        assertThat(client.exchange(RequestEntity.get(uri("/api/users/me")).headers(auth).build(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.exchange(RequestEntity.post(uri("/api/workspaces")).headers(auth)
                .contentType(MediaType.APPLICATION_JSON).body("{\"name\":\"Owner workspace\"}"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String workspaces = client.exchange(RequestEntity.get(uri("/api/workspaces")).headers(auth).build(), String.class)
                .getBody();
        long workspaceId = longField(workspaces, "id");
        ResponseEntity<String> projectResponse = client.exchange(RequestEntity.post(uri("/api/workspaces/" + workspaceId + "/projects"))
                .headers(auth).contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Reliability project\",\"description\":\"v2\"}"), String.class);
        assertThat(projectResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long projectId = longField(projectResponse.getBody(), "id");
        ResponseEntity<String> taskResponse = client.exchange(RequestEntity.post(uri("/api/projects/" + projectId + "/tasks"))
                .headers(auth).contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\":\"Verify durability\",\"priority\":\"MEDIUM\"}"), String.class);
        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(client.exchange(RequestEntity.post(uri("/api/workspaces/" + workspaceId + "/documents"))
                .headers(auth).contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\":\"Runbook\",\"content\":\"safe test content\"}"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> dashboard = client.exchange(RequestEntity.get(uri("/api/workspaces/" + workspaceId + "/dashboard"))
                .headers(auth).build(), String.class);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getBody()).contains("Reliability project", "Verify durability");
        assertThat(client.exchange(RequestEntity.get(uri("/api/workspaces/" + workspaceId + "/activities"))
                .headers(auth).build(), String.class).getBody()).contains("PROJECT_CREATED", "TASK_CREATED", "DOCUMENT_CREATED");

        ResponseEntity<String> secondRegister = jsonPost("/api/auth/register",
                "{\"email\":\"member-v2@example.com\",\"password\":\"password123\",\"name\":\"Member\"}");
        assertThat(secondRegister.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secondToken = login("member-v2@example.com", "password123", HttpStatus.OK).getBody()
                .replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(client.exchange(RequestEntity.get(uri("/api/workspaces/" + workspaceId + "/dashboard"))
                .headers(bearer(secondToken)).build(), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String requestId = "v2-request-id-42";
        ResponseEntity<String> requestIdResponse = client.exchange(RequestEntity.get(uri("/api/health"))
                .header("X-Request-Id", requestId).build(), String.class);
        assertThat(requestIdResponse.getHeaders().getFirst("X-Request-Id")).isEqualTo(requestId);
        ResponseEntity<String> generatedId = client.getForEntity("/api/health", String.class);
        assertThat(generatedId.getHeaders().getFirst("X-Request-Id")).matches("[0-9a-f-]{36}");

        verifyManagementSecurityAndHealth();
    }

    @Test
    void loginLimitReturns429RetryAfterAndWindowExpires() {
        jsonPost("/api/auth/register", "{\"email\":\"limit-v2@example.com\",\"password\":\"password123\",\"name\":\"Limited\"}");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(login("limit-v2@example.com", "wrong-password", HttpStatus.UNAUTHORIZED).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> limited = login("limit-v2@example.com", "wrong-password", HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(RETRY_AFTER))).isBetween(1L, 3L);
        assertThat(limited.getBody()).contains("Too many authentication attempts");
        await().atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(login("limit-v2@example.com", "wrong-password", HttpStatus.UNAUTHORIZED).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void twoIndependentRedisClientsShareAtomicAccountLimitAndKeysHidePii() throws Exception {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.getIp().setMaxAttempts(100);
        properties.getIp().setWindow(Duration.ofSeconds(20));
        properties.getAccount().setMaxAttempts(7);
        properties.getAccount().setWindow(Duration.ofSeconds(20));
        ClientAddressResolver addressResolver = new ClientAddressResolver("");
        LettuceConnectionFactory firstFactory = factory();
        LettuceConnectionFactory secondFactory = factory();
        try {
            StringRedisTemplate firstTemplate = template(firstFactory);
            StringRedisTemplate secondTemplate = template(secondFactory);
            RedisAuthRateLimiter first = new RedisAuthRateLimiter(firstTemplate, properties, addressResolver,
                    "integration-secret-for-hmac-key-generation", new SimpleMeterRegistry());
            RedisAuthRateLimiter second = new RedisAuthRateLimiter(secondTemplate, properties, addressResolver,
                    "integration-secret-for-hmac-key-generation", new SimpleMeterRegistry());
            CountDownLatch ready = new CountDownLatch(24);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger allowed = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(24);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int index = 0; index < 24; index++) {
                    RedisAuthRateLimiter limiter = index % 2 == 0 ? first : second;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        MockHttpServletRequest request = new MockHttpServletRequest();
                        request.setRemoteAddr("192.0.2.47");
                        try {
                            limiter.check(AuthAction.LOGIN, "same-account@example.com", request);
                            allowed.incrementAndGet();
                        } catch (TooManyAuthenticationAttemptsException ignored) {
                            // Expected after the shared account threshold is reached.
                        }
                        return null;
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                for (Future<?> future : futures) future.get();
                assertEquals(7, allowed.get());
                var keys = firstTemplate.keys("opspilot:auth:limit:login:*");
                assertThat(keys).isNotEmpty();
                assertThat(String.join(" ", keys)).doesNotContain("same-account@example.com", "192.0.2.47");
            } finally {
                executor.shutdownNow();
            }
        } finally {
            firstFactory.destroy();
            secondFactory.destroy();
        }
    }

    @Test
    void ipLimitAppliesAcrossDifferentAccountsAndRedisClients() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.getIp().setMaxAttempts(3);
        properties.getIp().setWindow(Duration.ofSeconds(20));
        properties.getAccount().setMaxAttempts(100);
        properties.getAccount().setWindow(Duration.ofSeconds(20));
        ClientAddressResolver addressResolver = new ClientAddressResolver("");
        LettuceConnectionFactory firstFactory = factory();
        LettuceConnectionFactory secondFactory = factory();
        try {
            RedisAuthRateLimiter first = new RedisAuthRateLimiter(template(firstFactory), properties, addressResolver,
                    "integration-secret-for-hmac-key-generation", new SimpleMeterRegistry());
            RedisAuthRateLimiter second = new RedisAuthRateLimiter(template(secondFactory), properties, addressResolver,
                    "integration-secret-for-hmac-key-generation", new SimpleMeterRegistry());

            for (int attempt = 0; attempt < 3; attempt++) {
                MockHttpServletRequest request = requestFrom("198.51.100.55");
                (attempt % 2 == 0 ? first : second)
                        .check(AuthAction.LOGIN, "account-" + attempt + "@example.test", request);
            }

            TooManyAuthenticationAttemptsException blocked = assertThrows(TooManyAuthenticationAttemptsException.class,
                    () -> second.check(AuthAction.LOGIN, "another-account@example.test",
                            requestFrom("198.51.100.55")));
            assertThat(blocked.getRetryAfterSeconds()).isBetween(1L, 20L);
        } finally {
            firstFactory.destroy();
            secondFactory.destroy();
        }
    }

    private static MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private void verifyManagementSecurityAndHealth() {
        String root = "http://127.0.0.1:" + managementPort;
        assertThat(client.getForEntity(root + "/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("http://127.0.0.1:" + apiPort + "/livez", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("http://127.0.0.1:" + apiPort + "/readyz", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity(root + "/actuator/health/authentication", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity(root + "/actuator/prometheus", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders metrics = new HttpHeaders();
        metrics.setAccept(List.of(MediaType.parseMediaType("text/plain;version=0.0.4;charset=utf-8")));
        metrics.setBasicAuth("v2-integration-metrics", "v2-integration-metrics-password");
        assertThat(client.exchange(RequestEntity.get(root + "/actuator/prometheus").headers(metrics).build(), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity(root + "/actuator/env", String.class).getStatusCode())
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> jsonPost(String path, String body) {
        return client.exchange(RequestEntity.post(uri(path)).contentType(MediaType.APPLICATION_JSON).body(body), String.class);
    }

    private ResponseEntity<String> login(String email, String password, HttpStatus expected) {
        ResponseEntity<String> result = jsonPost("/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertThat(result.getStatusCode()).isEqualTo(expected);
        return result;
    }

    private java.net.URI uri(String path) { return java.net.URI.create("http://localhost:" + apiPort + path); }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static long longField(String json, String field) {
        return Long.parseLong(json.replaceAll(".*\\\"" + field + "\\\":([0-9]+).*", "$1"));
    }

    private LettuceConnectionFactory factory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
