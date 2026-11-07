# V2 Production Backend — Acceptance Record

- **Updated:** 2026-11-08 (Australia/Sydney)
- **Status:** `READY FOR REVIEW` — PR #9 is open and not merged; backend and frontend CI passed after the final application and documentation updates.
- **Base:** `c9c6ed3bc8dcc5aee6cbb84616d797005470286b` (`origin/main` at implementation start)
- **Branch:** `feat/v2-production-backend`

This record reports observed checks only. No V1 migration, V1 API contract, or workspace authorization rule was changed.

## Verification results

| Area | Result | Evidence |
|---|---|---|
| Backend unit tests | **PASS** | Local isolated source-copy run: 48 tests, 0 failures, 0 errors, 0 skipped. The GitHub Java 21 job also passed all 48 unit tests. |
| Maven integration suite | **PASS on GitHub Actions** | Current-head `mvn clean verify` on Java 21.0.13+9 passed 48 unit tests and all 4 `V2ProductionBackendIT` tests. Testcontainers applied Flyway V1–V7; CI then built the backend image and confirmed its Java 21 runtime. The host JVM still cannot use Testcontainers with Docker Desktop's HTTP 400 Java-client response, so the full local Maven integration run remains unavailable. |
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
| GitHub CI | **PASS** | PR #9: backend verify (unit + Testcontainers integration), Java 21 backend image build/runtime check, and frontend install/test/lint/build jobs all passed on the final handoff changes. |
| Browser acceptance | **NOT RUN** | No browser controller was available in this session. |

## MySQL and resource boundary

- The original `opspilot-mysql` container remained healthy and untouched on port 3306. Its `opspilot_mysql_data` volume was not modified.
- V2 used only `opspilot-v2-mysql`, `opspilot-v2-redis`, `opspilot-v2-backend`, ports 3307/6379/8082/8083, and V2-named volumes. Outage tests stopped/restarted only the V2 MySQL or Redis container.
- No production credentials, paid API, or cloud resource was used. Temporary development credentials stayed in mode-restricted files outside Git and are removed with the V2 demo resources.
- No V1 Flyway migration or `docs/API_CONTRACT.md` content changed. Redis is used only for authentication rate limiting; no queue, SSE, broad cache, Kubernetes, or AI behavior was added.

## Remaining decisions and limitations

- **Testcontainers / CI:** Testcontainers still cannot use this Docker Desktop API from the host JVM. GitHub Actions ran the Docker-backed integration suite successfully on the current PR head, so this local tooling limitation does not block review.
- **R4:** the component-level regression passes; browser behavior remains unobserved.
- **`URGENT` task priority:** the backend enum still includes `URGENT`, while the frozen V1 contract lists `LOW | MEDIUM | HIGH`. Recommendation: keep V1 behavior frozen for this PR and have the owner decide whether support belongs in a separately versioned contract; do not silently change the enum, UI options, or historical data.
- **Development dependencies:** schedule review of the 10 moderate `minimatch`-related dev-tooling findings. Production dependencies currently have no audit findings. Avoid a forced ESLint/TypeScript-ESLint major change without a separate compatibility review.

## GitHub handoff

- All implementation and handoff changes are pushed to `feat/v2-production-backend`; the working tree is clean after the handoff commit.
- [PR #9](https://github.com/JoanH-project/opspilot/pull/9) targets `main`, is open, and is not merged. Backend and frontend CI passed on the final handoff changes.
- Do not merge as part of this verification. `URGENT` priority and development-tool audit advisories remain separate owner/review follow-ups.

## Reference documentation

- [Spring Boot 3.5 Actuator endpoints and security](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [Spring Data Redis scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
