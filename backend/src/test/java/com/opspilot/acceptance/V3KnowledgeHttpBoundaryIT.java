package com.opspilot.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.document.DocumentStatus;
import com.opspilot.document.DocumentRepository;
import com.opspilot.knowledge.DocumentIndexState;
import com.opspilot.knowledge.DocumentIndexStateRepository;
import com.opspilot.knowledge.DocumentIndexJobService;
import com.opspilot.knowledge.DocumentIndexStatus;
import com.opspilot.user.UserRepository;
import com.opspilot.workspace.WorkspaceMember;
import com.opspilot.workspace.WorkspaceMemberRepository;
import com.opspilot.workspace.WorkspaceRepository;
import com.opspilot.workspace.WorkspaceRole;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.flywaydb.core.Flyway;

/**
 * Real HTTP acceptance tests for V3 reindex authorization, size limits, and worker retry exhaustion.
 * The only replaced dependency is the private AI HTTP service; Spring, JWT, Redis, MySQL, Flyway,
 * workspace authorization, and the scheduled index worker run as configured by the application.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=127.0.0.1",
        "app.metrics.username=v3-boundary-metrics",
        "app.metrics.password=v3-boundary-test-metrics-password",
        "app.jwt.secret=v3-http-boundary-test-only-signing-key-0123456789",
        "app.jwt.expiration-seconds=86400",
        "app.auth.rate-limit.enabled=true",
        "app.auth.rate-limit.ip.max-attempts=1000",
        "app.auth.rate-limit.ip.window=10m",
        "app.auth.rate-limit.account.max-attempts=1000",
        "app.auth.rate-limit.account.window=10m",
        "app.knowledge.index-worker.enabled=true",
        "app.knowledge.index-worker.poll-interval=50"
})
@Import(V3KnowledgeHttpBoundaryIT.TestClockConfiguration.class)
class V3KnowledgeHttpBoundaryIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AI_INTERNAL_TOKEN = "v3-http-test-internal-token-only-0123456789";
    private static final String AI_ERROR_SENTINEL = "V3_TEST_AI_PRIVATE_ERROR_SENTINEL";
    private static final AtomicBoolean AI_FAILURES_ENABLED = new AtomicBoolean();
    private static final AtomicBoolean ALL_AI_REQUESTS_AUTHENTICATED = new AtomicBoolean(true);
    private static final AtomicInteger AI_INDEX_REQUESTS = new AtomicInteger();
    private static final AtomicReference<Instant> TEST_NOW = new AtomicReference<>(Instant.now());
    private static final ExecutorService AI_STUB_EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "v3-ai-http-test-stub");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpServer AI_STUB = startAiStub();

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("opspilot")
            .withUsername("opspilot_v3_test")
            .withPassword("v3-test-db-password-only");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void isolatedServices(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", mysql::getJdbcUrl);
        properties.add("spring.datasource.username", mysql::getUsername);
        properties.add("spring.datasource.password", mysql::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        properties.add("app.knowledge.ai-service-url",
                () -> "http://127.0.0.1:" + AI_STUB.getAddress().getPort());
        properties.add("app.knowledge.internal-token", () -> AI_INTERNAL_TOKEN);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository workspaceMembers;
    @Autowired DocumentRepository documents;
    @Autowired DocumentIndexStateRepository indexStates;
    @Autowired Flyway flyway;
    @Autowired MutableTestClock testClock;
    @LocalServerPort int apiPort;

    @BeforeEach
    void resetTestOnlyControls() {
        testClock.set(Instant.now());
        AI_FAILURES_ENABLED.set(false);
        ALL_AI_REQUESTS_AUTHENTICATED.set(true);
        AI_INDEX_REQUESTS.set(0);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");
    }

    @AfterAll
    static void stopAiStub() {
        AI_STUB.stop(0);
        AI_STUB_EXECUTOR.shutdownNow();
    }

    @Test
    void memberCannotReindexOrAffectWorkspaceTasksAndCooldown(TestReporter evidence) {
        AuthenticatedUser owner = registerAndLogin("owner");
        AuthenticatedUser member = registerAndLogin("member");
        long targetWorkspace = createWorkspace(owner.token(), "member-target");
        long otherWorkspace = createWorkspace(owner.token(), "member-control");

        workspaceMembers.saveAndFlush(new WorkspaceMember(
                workspaces.findById(targetWorkspace).orElseThrow(),
                users.findById(member.userId()).orElseThrow(), WorkspaceRole.MEMBER));

        long ownerDocument = insertActiveDocument(targetWorkspace, owner.userId());
        assertThat(indexStates.countByWorkspaceId(targetWorkspace)).isZero();
        int requestsBeforeOwner = AI_INDEX_REQUESTS.get();

        ResponseEntity<String> ownerReindex = postEmpty(reindexPath(targetWorkspace), owner.token());
        assertThat(ownerReindex.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(ownerReindex).path("queuedDocuments").asInt()).isEqualTo(1);
        assertThat(json(ownerReindex).path("status").asText()).isEqualTo("QUEUED");
        awaitReady(ownerDocument, requestsBeforeOwner + 1);

        StateSnapshot beforeDeniedRequest = stateSnapshot(ownerDocument);
        Instant limiterBeforeDeniedRequest = limiterTimestamp(targetWorkspace).orElseThrow();
        int requestsBeforeDeniedRequest = AI_INDEX_REQUESTS.get();

        ResponseEntity<String> memberReindex = postEmpty(reindexPath(targetWorkspace), member.token());
        assertThat(memberReindex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode forbidden = json(memberReindex);
        assertThat(forbidden.path("status").asInt()).isEqualTo(403);
        assertThat(forbidden.path("error").asText()).isEqualTo("Forbidden");
        assertThat(forbidden.path("message").asText()).isEqualTo("Insufficient workspace permissions");
        assertThat(stateSnapshot(ownerDocument)).isEqualTo(beforeDeniedRequest);
        assertThat(indexStates.countByWorkspaceId(targetWorkspace)).isEqualTo(1);
        assertThat(limiterTimestamp(targetWorkspace)).contains(limiterBeforeDeniedRequest);
        assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(requestsBeforeDeniedRequest);

        ResponseEntity<String> nonMemberReindex = postEmpty(reindexPath(otherWorkspace), member.token());
        assertThat(nonMemberReindex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(indexStates.countByWorkspaceId(otherWorkspace)).isZero();
        assertThat(limiterTimestamp(otherWorkspace)).isEmpty();

        ResponseEntity<String> otherOwnerReindex = postEmpty(reindexPath(otherWorkspace), owner.token());
        assertThat(otherOwnerReindex.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(otherOwnerReindex).path("queuedDocuments").asInt()).isZero();
        assertThat(indexStates.countByWorkspaceId(otherWorkspace)).isZero();
        assertThat(limiterTimestamp(otherWorkspace)).isPresent();
        assertThat(ALL_AI_REQUESTS_AUTHENTICATED).isTrue();
        evidence.publishEntry("v3.member-http-boundary",
                "ownerReindex=202; memberReindex=403; message=Insufficient workspace permissions; "
                        + "targetIndexRows=1 before/after; targetCooldownUnchanged=true; "
                        + "nonMemberOtherWorkspace=404; otherWorkspaceOwnerReindex=202; AIStubRequestsAuthenticated=true");
    }

    @Test
    void fiveHundredAndOneActiveDocumentsReturn413BeforeTaskOrCooldownWrites(TestReporter evidence) {
        AuthenticatedUser owner = registerAndLogin("size-owner");
        long oversizedWorkspace = createWorkspace(owner.token(), "size-501");
        long controlWorkspace = createWorkspace(owner.token(), "size-control");
        insertActiveDocuments(oversizedWorkspace, owner.userId(), 501);

        assertThat(documents.countByWorkspace_IdAndStatus(oversizedWorkspace, DocumentStatus.ACTIVE)).isEqualTo(501);
        assertThat(indexStates.countByWorkspaceId(oversizedWorkspace)).isZero();
        assertThat(limiterTimestamp(oversizedWorkspace)).isEmpty();

        ResponseEntity<String> firstReindex = postEmpty(reindexPath(oversizedWorkspace), owner.token());
        assert413(firstReindex);
        assertThat(indexStates.countByWorkspaceId(oversizedWorkspace)).isZero();
        assertThat(limiterTimestamp(oversizedWorkspace)).isEmpty();

        // A second 413, rather than a cooldown 429, proves the rejected request acquired no limiter row.
        ResponseEntity<String> repeatedReindex = postEmpty(reindexPath(oversizedWorkspace), owner.token());
        assert413(repeatedReindex);
        assertThat(documents.countByWorkspace_IdAndStatus(oversizedWorkspace, DocumentStatus.ACTIVE)).isEqualTo(501);
        assertThat(indexStates.countByWorkspaceId(oversizedWorkspace)).isZero();
        assertThat(limiterTimestamp(oversizedWorkspace)).isEmpty();

        jdbc.update("DELETE FROM documents WHERE workspace_id = ?", oversizedWorkspace);
        assertThat(documents.countByWorkspace_IdAndStatus(oversizedWorkspace, DocumentStatus.ACTIVE)).isZero();
        ResponseEntity<String> retryAfterFixtureRemoval = postEmpty(reindexPath(oversizedWorkspace), owner.token());
        assertThat(retryAfterFixtureRemoval.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(retryAfterFixtureRemoval).path("queuedDocuments").asInt()).isZero();
        assertThat(limiterTimestamp(oversizedWorkspace)).isPresent();

        ResponseEntity<String> controlReindex = postEmpty(reindexPath(controlWorkspace), owner.token());
        assertThat(controlReindex.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(controlReindex).path("queuedDocuments").asInt()).isZero();
        assertThat(indexStates.countByWorkspaceId(controlWorkspace)).isZero();
        assertThat(limiterTimestamp(controlWorkspace)).isPresent();
        evidence.publishEntry("v3.document-limit-http-boundary",
                "databaseActiveCount=501; repeatedReindex=413; error=Payload Too Large; "
                        + "message=Workspace reindex exceeds the supported document limit; fieldErrors={}; "
                        + "indexRows=0; limiterAbsentAfter413=true; reindexAfterFixtureRemoval=202; "
                        + "otherWorkspaceReindex=202");
    }

    @Test
    void realWorkerPersistsEightFailuresThenStopsAndProcessesAfterAiRecovery(TestReporter evidence) {
        AuthenticatedUser owner = registerAndLogin("retry-owner");
        long failedWorkspace = createWorkspace(owner.token(), "retry-exhaustion");
        long failedDocument = insertActiveDocument(failedWorkspace, owner.userId());
        assertThat(indexStates.findById(failedDocument)).isEmpty();
        assertThat(limiterTimestamp(failedWorkspace)).isEmpty();

        Instant wallStart = Instant.now();
        Instant testClockStart = testClock.instant();
        AI_FAILURES_ENABLED.set(true);
        int requestsBeforeTask = AI_INDEX_REQUESTS.get();
        ResponseEntity<String> accepted = postEmpty(reindexPath(failedWorkspace), owner.token());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(accepted).path("queuedDocuments").asInt()).isEqualTo(1);
        assertThat(json(accepted).path("status").asText()).isEqualTo("QUEUED");
        assertThat(limiterTimestamp(failedWorkspace)).isPresent();

        ResponseEntity<String> indexStatus = get(indexStatusPath(failedWorkspace), owner.token());
        assertThat(indexStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(indexStatus).path("total").asLong()).isEqualTo(1);

        List<StateSnapshot> observedFailures = new ArrayList<>();
        for (int attempt = 1; attempt <= 8; attempt++) {
            int expectedAttempt = attempt;
            int expectedRequestCount = requestsBeforeTask + attempt;
            await().pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                DocumentIndexState state = indexStates.findById(failedDocument).orElseThrow();
                assertThat(state.getStatus()).isEqualTo(DocumentIndexStatus.FAILED);
                assertThat(state.getAttempts()).isEqualTo(expectedAttempt);
                assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(expectedRequestCount);
            });

            StateSnapshot failure = stateSnapshot(failedDocument);
            observedFailures.add(failure);
            assertThat(failure.status()).isEqualTo(DocumentIndexStatus.FAILED);
            assertThat(failure.attempts()).isEqualTo(attempt);
            assertThat(failure.leaseUntil()).isNull();
            assertThat(failure.errorSummary()).isEqualTo("INDEXING_FAILED");
            assertThat(failure.errorSummary()).doesNotContain(AI_ERROR_SENTINEL, AI_INTERNAL_TOKEN);

            if (attempt < 8) {
                assertThat(failure.nextAttemptAt()).isNotNull();
                assertThat(failure.nextAttemptAt()).isAfter(testClock.instant());
                // Advance only the test-injected Clock; production retry intervals and MAX_ATTEMPTS are unchanged.
                testClock.set(failure.nextAttemptAt().plusMillis(1));
            } else {
                assertThat(failure.nextAttemptAt()).isNull();
            }
        }

        assertThat(observedFailures).hasSize(8);
        assertThat(observedFailures.subList(0, 7)).allSatisfy(state ->
                assertThat(state.nextAttemptAt()).isNotNull());
        assertThat(observedFailures.get(7).nextAttemptAt()).isNull();
        assertThat(ALL_AI_REQUESTS_AUTHENTICATED).isTrue();

        await().pollInterval(Duration.ofMillis(50)).during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(requestsBeforeTask + 8);
                    StateSnapshot terminal = stateSnapshot(failedDocument);
                    assertThat(terminal.status()).isEqualTo(DocumentIndexStatus.FAILED);
                    assertThat(terminal.attempts()).isEqualTo(8);
                    assertThat(terminal.nextAttemptAt()).isNull();
                    assertThat(terminal.leaseUntil()).isNull();
                    assertThat(terminal.errorSummary()).isEqualTo("INDEXING_FAILED");
                });

        AI_FAILURES_ENABLED.set(false);
        long recoveryWorkspace = createWorkspace(owner.token(), "retry-recovery");
        long recoveryDocument = insertActiveDocument(recoveryWorkspace, owner.userId());
        int requestsBeforeRecovery = AI_INDEX_REQUESTS.get();
        ResponseEntity<String> recoveryAccepted = postEmpty(reindexPath(recoveryWorkspace), owner.token());
        assertThat(recoveryAccepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json(recoveryAccepted).path("queuedDocuments").asInt()).isEqualTo(1);
        awaitReady(recoveryDocument, requestsBeforeRecovery + 1);
        assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(requestsBeforeRecovery + 1);
        assertThat(ALL_AI_REQUESTS_AUTHENTICATED).isTrue();
        evidence.publishEntry("v3.worker-retry-http-boundary",
                "reindex=202; AIHTTPFailures=8; persistedAttempts=8; intermediateNextAttemptAt=non-null; "
                        + "terminalStatus=FAILED; terminalNextAttemptAt=null; terminalLease=null; "
                        + "safeError=INDEXING_FAILED; ninthAttemptDuring1sObservation=false; recoveryNewTask=READY; "
                        + "simulatedClockAdvanceSeconds=" + Duration.between(testClockStart, testClock.instant()).toSeconds()
                        + "; wallElapsedMillis=" + Duration.between(wallStart, Instant.now()).toMillis());
    }

    @Test
    void realWorkerMakesExpiredEighthClaimTerminalWithoutCallingAiAgain(TestReporter evidence) {
        AuthenticatedUser owner = registerAndLogin("crashed-worker-owner");
        long workspaceId = createWorkspace(owner.token(), "crashed-final-claim");
        long documentId = insertActiveDocument(workspaceId, owner.userId());
        long revision = documents.findById(documentId).orElseThrow().getRevision();

        Instant now = testClock.instant();
        DocumentIndexState crashedAttempt = new DocumentIndexState(
                documentId, workspaceId, revision, now.minusSeconds(100));
        for (int attempt = 1; attempt <= DocumentIndexJobService.MAX_ATTEMPTS; attempt++) {
            crashedAttempt.claim(now.minusSeconds(100), now.minusSeconds(1), "crashed-claim-" + attempt);
        }
        indexStates.saveAndFlush(crashedAttempt);
        int aiRequestsBeforeRecovery = AI_INDEX_REQUESTS.get();

        await().pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            StateSnapshot terminal = stateSnapshot(documentId);
            assertThat(terminal.status()).isEqualTo(DocumentIndexStatus.FAILED);
            assertThat(terminal.attempts()).isEqualTo(DocumentIndexJobService.MAX_ATTEMPTS);
            assertThat(terminal.nextAttemptAt()).isNull();
            assertThat(terminal.leaseUntil()).isNull();
            assertThat(terminal.errorSummary()).isEqualTo("INDEXING_FAILED");
            assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(aiRequestsBeforeRecovery);
        });

        await().pollInterval(Duration.ofMillis(50)).during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(aiRequestsBeforeRecovery);
                    assertThat(stateSnapshot(documentId).status()).isEqualTo(DocumentIndexStatus.FAILED);
                });
        evidence.publishEntry("v3.worker-expired-final-lease-recovery",
                "syntheticCrashedState=INDEXING attempts=8 expiredLease; databaseQueryReclaimedRow=true; "
                        + "terminalStatus=FAILED; attempts=8; nextAttemptAt=null; leaseUntil=null; "
                        + "additionalAiRequests=0; ninthAttempt=false");
    }

    private AuthenticatedUser registerAndLogin(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "v3-" + label + "-" + suffix + "@example.test";
        String password = "integration-only-" + UUID.randomUUID();
        ResponseEntity<String> registration = postJson("/api/auth/register", null, Map.of(
                "email", email, "password", password, "name", "V3 Boundary " + label));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long userId = json(registration).path("id").asLong(-1);
        assertThat(userId).isPositive();

        ResponseEntity<String> login = postJson("/api/auth/login", null,
                Map.of("email", email, "password", password));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = json(login).path("accessToken").asText();
        assertThat(token).withFailMessage("Login response did not contain an access token").isNotBlank();
        return new AuthenticatedUser(userId, token);
    }

    private long createWorkspace(String token, String label) {
        ResponseEntity<String> response = postJson("/api/workspaces", token,
                Map.of("name", "V3 boundary " + label + " " + UUID.randomUUID()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = json(response).path("id").asLong(-1);
        assertThat(id).isPositive();
        return id;
    }

    private long insertActiveDocument(long workspaceId, long creatorId) {
        insertActiveDocuments(workspaceId, creatorId, 1);
        return jdbc.queryForObject("SELECT MAX(id) FROM documents WHERE workspace_id = ?", Long.class, workspaceId);
    }

    private void insertActiveDocuments(long workspaceId, long creatorId, int count) {
        String sql = """
                INSERT INTO documents (workspace_id, title, content, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """;
        List<Object[]> rows = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            rows.add(new Object[] {workspaceId, "V3 HTTP synthetic document " + index,
                    "Synthetic boundary test content.", creatorId});
        }
        jdbc.batchUpdate(sql, rows);
    }

    private void awaitReady(long documentId, int expectedRequestCount) {
        await().pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            DocumentIndexState state = indexStates.findById(documentId).orElseThrow();
            assertThat(state.getStatus()).isEqualTo(DocumentIndexStatus.READY);
            assertThat(state.getAttempts()).isEqualTo(1);
            assertThat(state.getNextAttemptAt()).isNull();
            assertThat(state.getLeaseUntil()).isNull();
            assertThat(AI_INDEX_REQUESTS.get()).isEqualTo(expectedRequestCount);
        });
    }

    private StateSnapshot stateSnapshot(long documentId) {
        DocumentIndexState state = indexStates.findById(documentId).orElseThrow();
        return new StateSnapshot(state.getStatus(), state.getAttempts(), state.getNextAttemptAt(),
                state.getLeaseUntil(), state.getLastErrorSummary());
    }

    private java.util.Optional<Instant> limiterTimestamp(long workspaceId) {
        List<Instant> rows = jdbc.query(
                "SELECT last_requested_at FROM workspace_knowledge_reindex_limits WHERE workspace_id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), workspaceId);
        return rows.stream().findFirst();
    }

    private void assert413(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode body = json(response);
        assertThat(body.path("status").asInt()).isEqualTo(413);
        assertThat(body.path("error").asText()).isEqualTo("Payload Too Large");
        assertThat(body.path("message").asText())
                .isEqualTo("Workspace reindex exceeds the supported document limit");
        assertThat(body.path("fieldErrors").isObject()).isTrue();
        assertThat(body.path("fieldErrors")).isEmpty();
    }

    private ResponseEntity<String> postEmpty(String path, String token) {
        HttpHeaders headers = bearer(token);
        RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.POST, uri(path));
        return http.exchange(request, String.class);
    }

    private ResponseEntity<String> postJson(String path, String token, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        RequestEntity<Map<String, ?>> request = new RequestEntity<>(body, headers, HttpMethod.POST, uri(path));
        return http.exchange(request, String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        RequestEntity<Void> request = new RequestEntity<>(bearer(token), HttpMethod.GET, uri(path));
        return http.exchange(request, String.class);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + apiPort + path);
    }

    private static String reindexPath(long workspaceId) {
        return "/api/workspaces/" + workspaceId + "/knowledge/reindex";
    }

    private static String indexStatusPath(long workspaceId) {
        return "/api/workspaces/" + workspaceId + "/knowledge/index-status";
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (IOException exception) {
            // Avoid attaching raw responses to test failures; login bodies contain a JWT.
            throw new AssertionError("Expected a JSON API response");
        }
    }

    private static HttpServer startAiStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(AI_STUB_EXECUTOR);
            server.createContext("/internal/v1/documents/index", V3KnowledgeHttpBoundaryIT::handleIndexRequest);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleIndexRequest(HttpExchange exchange) throws IOException {
        try {
            AI_INDEX_REQUESTS.incrementAndGet();
            String serviceToken = exchange.getRequestHeaders().getFirst("X-Opspilot-Service-Token");
            if (!AI_INTERNAL_TOKEN.equals(serviceToken)) {
                ALL_AI_REQUESTS_AUTHENTICATED.set(false);
                sendJson(exchange, 401, "{}");
                return;
            }
            if (AI_FAILURES_ENABLED.get()) {
                sendJson(exchange, 503, "{\"message\":\"" + AI_ERROR_SENTINEL + "\"}");
                return;
            }
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            long revision = request.path("revision").asLong(-1);
            if (revision < 0) {
                sendJson(exchange, 400, "{}");
                return;
            }
            sendJson(exchange, 200, "{\"revision\":" + revision + ",\"chunks_indexed\":1}");
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record AuthenticatedUser(long userId, String token) { }

    private record StateSnapshot(DocumentIndexStatus status, int attempts, Instant nextAttemptAt,
                                 Instant leaseUntil, String errorSummary) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock(TEST_NOW, ZoneOffset.UTC);
        }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        MutableTestClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableTestClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
