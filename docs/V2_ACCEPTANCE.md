# V2 Production Backend — Acceptance Record

- **Updated:** 2026-11-08 (Australia/Sydney)
- **Status:** `BLOCKED` — local isolated Compose acceptance passed, but the Maven Testcontainers suite cannot initialize Docker through this machine's Java Docker client. The branch is pushed; GitHub rejected PR creation with `403 Resource not accessible by integration`, so PR checks have not started.
- **Base:** `c9c6ed3bc8dcc5aee6cbb84616d797005470286b` (`origin/main` at implementation start)
- **Branch:** `feat/v2-production-backend`

This record reports observed checks only. No V1 migration, V1 API contract, or workspace authorization rule was changed.

## Verification results

| Area | Result | Evidence |
|---|---|---|
| Backend unit tests | **PASS** | `cd backend && mvn -B -ntp clean test`: 47 tests, 0 failures, 0 errors, 0 skipped. Compiled with `--release 21` on the host JDK 26. |
| Maven integration suite | **BLOCKED locally** | `mvn -B -ntp clean verify` passed all 47 unit tests, then Testcontainers 1.21.0 failed before test methods ran. Docker Desktop's API returned HTTP 400 with an empty Docker `Info` response to the Java Docker client, despite Docker CLI/Compose working. The suite covers HTTP workflows, workspace isolation, management security, account limit/TTL, concurrent sharing across two Redis clients, PII-free keys, and the source-IP limit across different accounts. No Testcontainers method is counted as passed. |
| Isolated Docker runtime | **PASS** | Dedicated V2 MySQL 8.4, Redis 7.4, and backend containers became healthy; Flyway validated all 7 existing migrations; Hibernate validation and application startup succeeded. The backend image runs Temurin Java 21.0.13. |
| HTTP integration | **PASS** | A temporary local acceptance script completed 32 assertions against the real isolated stack: exact V1 health response; register/login, duplicate 409, validation 400, invalid credentials/JWT 401; `/api/users/me`; workspace/project/task/document writes; Dashboard/activity; other-workspace 404; request IDs; CORS preflight; health, readiness/liveness, and Actuator/Prometheus access. The script and temporary credentials are not tracked. |
| Redis rate limit and expiry | **PASS** | On the isolated stack, configured a 3-attempt/20-second account window: allowed responses retained 401, the next request returned 429 with `Retry-After`, and a valid login recovered after expiry. |
| Source-IP limit | **PASS** | Temporarily configured the isolated HTTP backend for 3 attempts/20 seconds and 100 account attempts. Three wrong-password requests for three distinct accounts from the same source returned 401; the fourth returned 429 with `Retry-After`. |
| Shared rate limit across backends | **PASS** | Started a second temporary backend on the same isolated MySQL/Redis network. Alternated 20 wrong-password attempts across both instances; each returned 401. The next request through the other instance returned 429 with `Retry-After`. The second container and its temporary environment file were removed. |
| Redis outage and recovery | **PASS** | With only V2 Redis stopped, auth readiness returned 503, liveness stayed 200, login failed closed with 503, and an already-authenticated workspace read remained 200. The first login immediately after the Redis container became healthy returned 503; the next readiness probe returned 200 and login recovered to 200. |
| MySQL outage and recovery | **PASS after scoped fix** | With only V2 MySQL stopped, login and valid-token `/api/users/me` and Dashboard requests returned sanitized 503; liveness stayed 200 and readiness returned 503. After restart, login and the existing-token Dashboard request returned 200. The failure exposed JPA transaction-start exceptions that are not `DataAccessException`; `TransactionException` is now mapped to 503 in both the JWT filter and API exception handler, with regression tests. |
| Startup without MySQL | **PASS** | A one-off backend start with only V2 MySQL stopped exited nonzero on Flyway/database connection failure. V2 MySQL was restarted and returned healthy. |
| Graceful shutdown | **PASS** | The V2 backend stopped within its configured Compose grace period; a Spring graceful-shutdown log marker was observed. Restart returned the backend to healthy and `GET /api/health` to 200 with `{"status":"UP"}`. |
| Auth R4 regression | **PASS, browser not run** | Frontend component test verifies login success followed by `/api/users/me` failure leaves no token persisted. Real browser fault injection was not available; **BROWSER NOT RUN**. |
| Frontend install/tests/lint/build | **PASS** | Using already-installed Node 24.19.0: `npm ci`; `npm test` (27 tests in 4 files); `npm run lint`; `npm run build`. The default Node 24.2.0 is below several locked packages' declared engine minimums, so verification used 24.19.0. |
| npm audit | **10 moderate dev advisories remain** | Current full audit: 0 low, 10 moderate, 0 high, 0 critical. The findings are in development tooling and trace to `minimatch` globparts amplification (GHSA-g3hv-75wg-m249), propagated through ESLint/TypeScript-ESLint. `npm audit --omit=dev` reports 0. npm reports only semver-major tooling changes as fixes; no `--force` update was applied. |
| GitHub CI | **BLOCKED** | No PR exists, so the required PR workflow has not run. Create a PR from `feat/v2-production-backend` to `main`, then verify Backend CI on the latest head SHA. No result from an older V1 SHA is used as V2 evidence. |
| Browser acceptance | **NOT RUN** | No browser controller was available in this session. |

## MySQL and resource boundary

- The original `opspilot-mysql` container remained healthy and untouched on port 3306. Its `opspilot_mysql_data` volume was not modified.
- V2 used only `opspilot-v2-mysql`, `opspilot-v2-redis`, `opspilot-v2-backend`, ports 3307/6379/8082/8083, and V2-named volumes. Outage tests stopped/restarted only the V2 MySQL or Redis container.
- No production credentials, paid API, or cloud resource was used. Temporary development credentials stayed in mode-restricted files outside Git and are removed with the V2 demo resources.
- No V1 Flyway migration or `docs/API_CONTRACT.md` content changed. Redis is used only for authentication rate limiting; no queue, SSE, broad cache, Kubernetes, or AI behavior was added.

## Remaining decisions and limitations

- **Testcontainers / CI:** Docker CLI and real Compose integration work locally, but Testcontainers cannot use this Docker Desktop API from the host JVM. The Docker-backed integration suite must pass on GitHub Actions before review is considered ready.
- **R4:** the component-level regression passes; browser behavior remains unobserved.
- **`URGENT` task priority:** the backend enum still includes `URGENT`, while the frozen V1 contract lists `LOW | MEDIUM | HIGH`. Recommendation: keep V1 behavior frozen for this PR and have the owner decide whether support belongs in a separately versioned contract; do not silently change the enum, UI options, or historical data.
- **Development dependencies:** schedule review of the 10 moderate `minimatch`-related dev-tooling findings. Production dependencies currently have no audit findings. Avoid a forced ESLint/TypeScript-ESLint major change without a separate compatibility review.

## GitHub handoff

- Commit `30e32b9a5b1c80dbbeedc7ae3131de6a4b4c51b2` is pushed to `feat/v2-production-backend`; working tree was clean after the push.
- The connected GitHub integration returned `403 Resource not accessible by integration` when asked to create the PR. No PR number or URL exists yet; no alternate write path was attempted.
- Open [the compare page](https://github.com/JoanH-project/opspilot/compare/main...feat/v2-production-backend?expand=1) and create one PR targeting `main`. Then review the current-head Backend CI result before marking it ready or merging.

## Reference documentation

- [Spring Boot 3.5 Actuator endpoints and security](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [Spring Data Redis scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
