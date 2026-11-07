# Environment Variables

Concise reference for local OpsPilot backend and MySQL setup. Values shown are defaults unless marked required.

## Docker Compose (MySQL)

Docker Compose reads a `.env` file from the repository root automatically. Copy `.env.example` to `.env` and adjust the passwords before starting MySQL.

| Variable | Required | Default | Used by | Purpose |
|---|---|---|---|---|
| `DB_NAME` | No | `opspilot` | Docker Compose, backend | MySQL database name |
| `DB_USERNAME` | No | `opspilot` | Docker Compose, backend | Application database user |
| `DB_PASSWORD` | **Yes** | — | Docker Compose, backend | Application database password |
| `MYSQL_ROOT_PASSWORD` | **Yes** | — | Docker Compose | MySQL root password |
| `MYSQL_PORT` | No | `3306` | Docker Compose | Host port mapped to MySQL |

If `DB_PASSWORD` or `MYSQL_ROOT_PASSWORD` is missing, `docker compose up` fails with a clear error.

## Backend (Spring Boot)

The backend does not load `.env` automatically. Export the same database values in your shell, or set them in your IDE/run configuration, before `mvn spring-boot:run`.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_HOST` | No | `localhost` | MySQL host |
| `DB_PORT` | No | `3306` | MySQL port |
| `DB_NAME` | No | `opspilot` | Database name |
| `DB_USERNAME` | No | `opspilot` | Database user |
| `DB_PASSWORD` | **Yes** | — | Database password. No fallback is configured. |
| `JWT_SECRET` | No | development-only fallback in `application.yml` | JWT signing secret. Set a strong value outside local development. |
| `JWT_EXPIRATION_SECONDS` | No | `3600` | Access token lifetime in seconds |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Comma-separated browser origin allow-list |
| `SERVER_PORT` | No | `8080` | HTTP port for the backend |

## Frontend (Vite)

Copy `frontend/.env.example` to `frontend/.env`. The frontend does not load the repository root `.env`.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `VITE_API_BASE_URL` | No | `http://localhost:8080` when unset in code | Backend API base URL for browser requests |

## Notes

- Never commit real secrets. `.env` is gitignored.
- Automated backend tests (`mvn clean test`) do not require MySQL or these variables.
- Running the backend against MySQL requires `DB_PASSWORD` at minimum, plus a reachable MySQL instance with matching credentials.

## V2 production profile

Activate with `SPRING_PROFILES_ACTIVE=prod`. Production requires explicit DB and Redis credentials, a high-entropy random `JWT_SECRET`, exact HTTPS `CORS_ALLOWED_ORIGINS`, Redis TLS, a random metrics password, and a management bind address. The production JDBC URL uses `sslMode=VERIFY_IDENTITY`; provision the schema and database TLS truststore before starting. Missing or unsafe values fail before datasource initialization.

| Variable | Required / default | Purpose |
|---|---|---|
| `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Required | Pre-created MySQL schema and application user |
| `DB_PORT`, `DB_SSL_MODE` | `3306`, `VERIFY_IDENTITY` | MySQL TLS identity verification |
| `DB_CONNECT_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_MS` | `5000`, `15000` | Bounded JDBC waits |
| `REDIS_HOST`, `REDIS_PASSWORD` | Required | Authentication rate-limit storage |
| `REDIS_PORT`, `REDIS_SSL_ENABLED` | `6379`, `true` | Redis endpoint; TLS is enforced in prod |
| `JWT_SECRET` | Required, random Base64URL, at least 32 decoded bytes | JWT signing key; development value is rejected |
| `CORS_ALLOWED_ORIGINS` | Required exact HTTPS origins | Browser allow-list; wildcard and `null` are rejected |
| `AUTH_RATE_LIMIT_IP_MAX_ATTEMPTS`, `AUTH_RATE_LIMIT_IP_WINDOW` | `60`, `60s` | Per-address limit |
| `AUTH_RATE_LIMIT_ACCOUNT_MAX_ATTEMPTS`, `AUTH_RATE_LIMIT_ACCOUNT_WINDOW` | `20`, `10m` | Per-normalized-account limit |
| `AUTH_RATE_LIMIT_TRUSTED_PROXIES` | Empty | Comma-separated proxy IPs allowed to assert X-Forwarded-For |
| `METRICS_USERNAME`, `METRICS_PASSWORD` | Required | Protected Prometheus endpoint credentials |
| `MANAGEMENT_SERVER_ADDRESS`, `MANAGEMENT_SERVER_PORT` | address required, port `8081` | Private management listener |

Development-only limiter switches, metrics credentials, JDBC flags, and the JWT fallback are defined in `application.yml`; production overrides them in `application-prod.yml`. See [V2 production backend](V2_PRODUCTION_BACKEND.md) for commands, operational behavior, and proxy guidance.
