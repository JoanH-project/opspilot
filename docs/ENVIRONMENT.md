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
