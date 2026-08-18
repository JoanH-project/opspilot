# OpsPilot

OpsPilot is a modular Spring Boot backend foundation for an operations workspace. Phase 1 intentionally contains only infrastructure: a MySQL connection, Flyway migrations, and a health endpoint.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (or another Docker Compose-compatible runtime)

## Start MySQL

From the repository root, choose local development passwords and export them for Docker Compose and the backend:

```bash
export DB_NAME=opspilot
export DB_USERNAME=opspilot
export DB_PASSWORD='choose-a-local-development-password'
export MYSQL_ROOT_PASSWORD='choose-a-different-root-password'
docker compose up -d
```

Check that MySQL is ready:

```bash
docker compose ps
```

## Run the backend

Keep `DB_PASSWORD` exported from the previous step, then run:

```bash
cd backend
mvn spring-boot:run
```

The application connects to `localhost:3306` by default. Override database settings with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` when necessary.

Once running, verify the endpoint:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

Flyway automatically applies `src/main/resources/db/migration/V1__init.sql` on startup.

## Run tests

```bash
cd backend
mvn test
```

The health endpoint test uses Spring MVC's test support and does not require a running MySQL instance.

## Stop MySQL

```bash
docker compose down
```

Use `docker compose down -v` only when you explicitly want to delete the local MySQL data volume.
