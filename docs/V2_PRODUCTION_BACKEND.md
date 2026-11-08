# V2 Production Backend

V2 adds a small set of production-oriented controls around the frozen V1 API: fail-fast production configuration, shared authentication rate limiting, protected health/metrics endpoints, request correlation, and an isolated Docker demo. V1 data models, Flyway V1–V7, authorization rules, and `docs/API_CONTRACT.md` remain unchanged.

## Architecture choices

- **Redis is used only for login and registration rate limiting.** Lua performs the two-dimension check and counter/TTL updates in one Redis operation, so multiple backend instances share a consistent window. There is no application cache, queue, event stream, or background job workload in V2.
- **MySQL and Flyway remain the source of truth.** Production refuses insecure JDBC flags and requires MySQL TLS identity verification. There is no new migration.
- **Actuator uses a separate management port.** Health endpoints expose status only. Prometheus is protected by HTTP Basic with the `METRICS` role; unlisted actuator endpoints are denied.
- **Request IDs are bounded and safe.** A valid `X-Request-Id` (`[A-Za-z0-9._-]{1,64}`) is echoed; otherwise a UUID is created. The response header and MDC use that value. Request logs record method, route template, status, and duration only; they do not log bodies, query strings, credentials, bearer tokens, or raw email addresses.
- **No write retries are configured.** Failed writes return an error; they are never reported as successful or automatically replayed.

## Local V2 Docker demo

This Compose file is intentionally separate from the original `docker-compose.yml`. It uses containers `opspilot-v2-mysql`, `opspilot-v2-redis`, and `opspilot-v2-backend`, volumes `opspilot_v2_mysql_data` and `opspilot_v2_redis_data`, and loopback host ports 3307, 6379, 8082, and 8083. It does not reuse or modify `opspilot-mysql`, port 3306, or `opspilot_mysql_data`.

Generate local-only credentials in the shell, then start the stack:

```bash
export V2_DB_PASSWORD="$(openssl rand -hex 24)"
export V2_MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export V2_REDIS_PASSWORD="$(openssl rand -hex 24)"
export V2_JWT_SECRET="$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n')"
export V2_METRICS_PASSWORD="$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n')"
docker compose -f docker-compose.v2.yml up -d --build
docker compose -f docker-compose.v2.yml ps
curl http://localhost:8082/api/health
curl http://localhost:8083/actuator/health
```

The environment is local and disposable. Never use production credentials here. To stop the demo while retaining its data:

```bash
docker compose -f docker-compose.v2.yml down
```

Only if you intentionally want to delete the **V2 demo's** data, remove the two V2 volumes explicitly; never run a global prune and never include the original Compose project.

### Rate-limit demonstration

By default the demo allows 20 attempts per normalized account per 10 minutes and 60 attempts per source address per minute. For a short local demonstration, set these before startup:

```bash
export V2_AUTH_RATE_LIMIT_ACCOUNT_MAX_ATTEMPTS=3
export V2_AUTH_RATE_LIMIT_ACCOUNT_WINDOW=20s
```

Restart the V2 backend after changing those variables. Three failed logins for one email are allowed; the next returns `429 Too Many Requests` and `Retry-After`. A duplicate registration continues to return `409` until the configured limit is reached. Counter keys contain only an HMAC digest and action name; email, IP, password, and token values are not stored as key text or metric labels.

When the application is behind a proxy, configure `V2_AUTH_RATE_LIMIT_TRUSTED_PROXIES` with exact proxy IP addresses. Forwarded headers from any other peer are ignored. Do not trust client-supplied `X-Forwarded-For` directly.

### Failure rehearsal

With an existing bearer token, stop Redis:

```bash
docker compose -f docker-compose.v2.yml stop redis
```

Login and registration return a generic `503` with no backend details. Authenticated business endpoints do not call the limiter; liveness stays up, the authentication health group/readiness report the unavailable limiter, and no login attempt is silently allowed. Restore service with:

```bash
docker compose -f docker-compose.v2.yml start redis
```

Stopping MySQL makes readiness unhealthy and database-backed operations return a generic `503` when the failed connection is used. Startup fails if MySQL/Flyway cannot connect. There is no automatic retry for POST/PATCH operations.

## Production profile

Set `SPRING_PROFILES_ACTIVE=prod`. These values have **no development secret fallback** in `application-prod.yml`:

| Variable | Requirement / default | Purpose |
|---|---|---|
| `DB_HOST` | Required | MySQL hostname covered by the certificate |
| `DB_PORT` | `3306` | MySQL TLS endpoint |
| `DB_NAME` | Required; pre-create the schema | Flyway schema target; production never creates the database |
| `DB_USERNAME`, `DB_PASSWORD` | Required | Least-privilege application user |
| `DB_SSL_MODE` | `VERIFY_IDENTITY` | MySQL certificate and hostname verification; install the trusted CA in the JVM truststore |
| `DB_CONNECT_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_MS` | `5000`, `15000` | Bounded JDBC connect/read waits |
| `DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_MAX_LIFETIME_MS` | `5000`, `10`, `2`, `1800000` | Hikari pool bounds |
| `REDIS_HOST`, `REDIS_PASSWORD` | Required | Authentication limiter Redis |
| `REDIS_PORT` | `6379` | Redis endpoint |
| `REDIS_SSL_ENABLED` | `true`; production validator requires true | Redis TLS |
| `REDIS_CONNECT_TIMEOUT`, `REDIS_COMMAND_TIMEOUT` | `2s`, `2s` | Bounded Redis waits |
| `JWT_SECRET` | Required; unique random URL-safe Base64, at least 32 decoded bytes and entropy checks | JWT signing; known development values are rejected |
| `JWT_EXPIRATION_SECONDS` | `3600` | Existing access-token lifetime |
| `CORS_ALLOWED_ORIGINS` | Required exact HTTPS origins; no wildcard, `null`, path, or localhost | Browser origin allow-list |
| `AUTH_RATE_LIMIT_IP_MAX_ATTEMPTS`, `AUTH_RATE_LIMIT_IP_WINDOW` | `60`, `60s` | Per-source-address window |
| `AUTH_RATE_LIMIT_ACCOUNT_MAX_ATTEMPTS`, `AUTH_RATE_LIMIT_ACCOUNT_WINDOW` | `20`, `10m` | Per-normalized-account window |
| `AUTH_RATE_LIMIT_TRUSTED_PROXIES` | Empty by default | Exact proxy IP allow-list before forwarded address headers are considered |
| `METRICS_USERNAME`, `METRICS_PASSWORD` | Required; password must pass the same random-secret check | Prometheus HTTP Basic credentials |
| `MANAGEMENT_SERVER_ADDRESS` | Required | Bind management listener only to a private interface reachable by health/metrics scrapers |
| `MANAGEMENT_SERVER_PORT` | `8081` | Management listener, distinct from API port |

The production validator runs before database initialization and rejects missing secrets/config, weak or development JWT/metrics credentials, non-HTTPS/wildcard CORS, unsafe JDBC flags, missing `sslMode=VERIFY_IDENTITY`, missing Redis credentials/TLS, disabled rate limiting, and missing management binding. Production MySQL schema creation and grants are operator-managed; Flyway remains the only schema migration mechanism.

## Health, metrics, and request logs

- Existing `GET /api/health` still returns exactly `{"status":"UP"}`.
- `/livez` reports process liveness without MySQL/Redis dependency checks.
- `/readyz` includes MySQL and the auth-protection dependency; Redis failure makes readiness unhealthy while liveness remains healthy.
- `/actuator/health` and health groups reveal only `UP`/`DOWN`, not component details.
- `/actuator/prometheus` is available only on the management listener and requires the configured metrics account. The separate management listener must remain private; basic auth is not a reason to publish it broadly.
- Standard Spring/Micrometer HTTP, JVM, and datasource-pool metrics are available. Rate-limit counters carry only the low-cardinality action tag (`login` or `register`).
- `X-Request-Id` is accepted in CORS requests and exposed to browser callers.

## Verification boundaries and open items

- `docs/V2_ACCEPTANCE.md` records command outputs and distinguishes local, CI, integration, and browser evidence. Testcontainers failures are not skipped or reported as passes.
- Auth R4 has a frontend component regression for login 200 followed by `/api/users/me` failure. A real browser run remains `BROWSER NOT RUN` when no browser controller is available.
- The `URGENT` task-priority difference remains an owner decision; V2 does not change the backend enum or frozen V1 contract.
- The production frontend dependency audit has no advisories; the development tree still has advisories listed in the acceptance record. V2 only applies non-force in-range fixes with lint/test/build evidence.
