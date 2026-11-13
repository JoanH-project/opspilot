# V3 HTTP boundary acceptance — 2026-11-13

**Result: PASS — `V3_READY_FOR_HUMAN_REVIEW`.** The three previously outstanding real HTTP checks ran in isolated GitHub Actions Testcontainers infrastructure. PR #10 remains OPEN and unmerged.

## Execution

- PR: [#10](https://github.com/JoanH-project/opspilot/pull/10), branch `feat/v3-knowledge-rag`.
- Acceptance code SHA: `b8adb8903724f918ecb518da8114fe38a0c4a2b2`.
- Backend workflow: [run 43378130105](https://github.com/JoanH-project/opspilot/actions/runs/43378130105), Ubuntu 24.04, Java 21. The PR workflow checked out merge ref `f55b67666fb260187579b52ff76f009501454825`, containing the stated head.
- Runtime: Spring Boot `RANDOM_PORT` + `TestRestTemplate`, real JWT registration/login, MySQL 8.4 Testcontainers, Redis 7.4 Testcontainers, Flyway V1–V8, real JPA authorization and reindex limiter, and the scheduled production Worker.
- Test-only substitute: a JDK HTTP server bound to `127.0.0.1` replaced only the private AI HTTP dependency. It checked the internal token and returned controlled `503` failures or a success response. Backend, Redis, database, HTTP routes, JWT, and Worker were not mocked.
- Test data: generated users/workspaces and 501 synthetic documents existed only in the disposable CI MySQL instance. Testcontainers lifecycle and hosted-runner teardown removed the environment. The test made no connection to Joan's machine or existing MySQL volume.

## Results

### MEMBER authorization and workspace isolation

Test: `memberCannotReindexOrAffectWorkspaceTasksAndCooldown` — **PASS**.

- Register/login for owner and member used HTTP. The owner created two workspaces; the MEMBER relation was persisted in isolated MySQL.
- Owner reindex on the populated workspace: `202`, `{"queuedDocuments":1,"status":"QUEUED"}`.
- MEMBER reindex on that workspace: `403`, `{"status":403,"error":"Forbidden","message":"Insufficient workspace permissions"}`.
- Non-member request on the other workspace: `404`; its owner then reindexed it successfully with `202`.
- Database assertions: the target's index-state row count and limiter timestamp were unchanged across the denied request; no Worker/AI call was caused by the denial. The separate workspace remained operable.

### 501 ACTIVE documents

Test: `fiveHundredAndOneActiveDocumentsReturn413BeforeTaskOrCooldownWrites` — **PASS**.

- MySQL count before the request: 501 ACTIVE documents.
- Two actual owner reindex HTTP requests returned `413` with the safe API structure `{"status":413,"error":"Payload Too Large","message":"Workspace reindex exceeds the supported document limit","fieldErrors":{}}`.
- Database assertions after both requests: no index-state rows and no limiter row. Once only the synthetic documents were deleted, the workspace accepted reindex with `202`; a different workspace also accepted with `202`. No index task or AI inference was created by the rejected requests.

### Worker retries exhausted and recovery

Test: `realWorkerPersistsEightFailuresThenStopsAndProcessesAfterAiRecovery` — **PASS**.

- Reindex HTTP: `202`, `queuedDocuments=1`, `status=QUEUED`; index-status HTTP: `200`.
- The real Worker called the localhost AI HTTP stub eight times and the stub returned `503` each time. Database state was checked after each persisted failure.
- Attempts 1–7 each had a non-null next retry time. At attempt 8, the final database row was `FAILED`, `attempts=8`, `next_attempt_at=NULL`, `lease_until=NULL`, and `last_error_summary=INDEXING_FAILED`. The private sentinel and internal token were absent from the stored error.
- A further one-second wall-clock observation showed no ninth request. The test then switched only the stub to success; an independently created task was accepted over HTTP and reached `READY` through the real Worker.
- Test `Clock` advancement simulated the unchanged production backoff schedule (about 10m15s); the JUnit test method elapsed 1.853s wall time. Production `MAX_ATTEMPTS=8` and backoff configuration were not modified.

## JUnit evidence and CI

Backend CI reports `V3KnowledgeHttpBoundaryIT`: 3 tests, 0 failures, 0 errors, 0 skipped; suite time 12.193s. [JUnit artifact](https://github.com/JoanH-project/opspilot/actions/runs/43378130105/artifacts/14015283475) is retained for 7 days. CI logs show successful Flyway V1–V8 application, application startup, route statuses, and eight sanitized Worker failure records; they do not contain test JWTs, passwords, or the AI error sentinel.

All three workflows completed successfully for acceptance code SHA `b8adb8903724f918ecb518da8114fe38a0c4a2b2`:

- [Backend CI — run 43378130105](https://github.com/JoanH-project/opspilot/actions/runs/43378130105)
- [AI Service CI — run 43378130078](https://github.com/JoanH-project/opspilot/actions/runs/43378130078)
- [Frontend CI — run 43378129996](https://github.com/JoanH-project/opspilot/actions/runs/43378129996)

The small local retrieval fixture remains synthetic regression evidence only: 13 document revisions and 23 queries produced Recall@5 1.0, Hit@5 1.0, MRR@5 0.9333, and 8/8 negative queries empty at the chosen threshold. It is not a production-quality guarantee. Prior real browser registration, login, indexing, search, cited answers, injection-text handling, insufficient-evidence response, and `202`/`429` plus readable `Retry-After: 300` remain separate previously observed evidence.

## Local environment and scope

Docker Desktop on the developer machine remains unresponsive. No local `mvn clean verify` was run or claimed in this acceptance. Local test compilation passed and `mvn test` passed 55 unit tests; these do not substitute for the CI integration run. This local daemon issue does not block this completed cloud acceptance.

No V1 API contract or V1–V7 migration was changed; V8 remains the V3 migration. No existing local `opspilot-mysql` container or volume was touched. Qdrant remains derived/rebuildable data, and local E5/Ollama remains the zero-paid-inference design. No V4 work or merge was performed.
