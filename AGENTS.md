# AGENTS.md

Dealership vehicle-service **Appointment** booking and **Reminder** delivery. Modular Spring Boot monolith. **Docs are the source of truth. Do not write application code until the user asks to implement.**

## Read first

1. [CONTEXT.md](CONTEXT.md) — glossary only.
2. [docs/prd/README.md](docs/prd/README.md) — product, one file per module.
3. [docs/trd/README.md](docs/trd/README.md) — tech, one file per module.
4. [docs/decision/README.md](docs/decision/README.md) — architecture I chose and why.
5. [docs/architecture.md](docs/architecture.md) — runtime flows.
6. [docs/testing/README.md](docs/testing/README.md) — unit, integration, e2e, uniqueness proof.
7. [docs/adr/](docs/adr/) — short ADRs.

Ignore [no-push/](no-push/) except the assignment PDF.

## Constraints

- No Kafka, no Keycloak, no Spring Cloud Gateway, no Lombok.
- Reminder offsets from config (default 24h, 2h). No confirmation mail in v1.
- Mail workers 2–4 (default 2), 30s lease with heartbeat, SMTP timeout < lease.
- Redis is Bucket4j HTTP limits only, never uniqueness or mail throttle.
- Reminder uniqueness and one Confirmed Appointment per Vehicle are **PostgreSQL**.
- Staff book **home Dealership only**.
- List GETs are paginated and searchable (`q`). Customer/Staff reads: own or home shop, else 404.
- Shop-floor In Progress/Completed is later.
- JWT access token 1 day. Idempotency-Key TTL 24h.
- Do not log raw contact or full VIN. Default Notification Mode is stub.
- Store `scheduled_at` UTC and `display_offset` from `scheduledAt`. No Customer timezone field. Mail = Booking Offset. Staff GET = Dealership Timezone. JVM UTC (EC2 region irrelevant).
- Clock math is PostgreSQL (set-based `interval`, claim `WHERE`, no-show `UPDATE`). Do not load full graphs to subtract hours. Outbox carries a lean snapshot for mail.
- Comments in Java: only non-obvious why. Swagger via springdoc 2.8.x (Boot 3), not 3.x.
- Reuse: one implementation per job (page, 404, time format, Reminder SQL, sender). No copy-paste. See [docs/trd/code-style.md](docs/trd/code-style.md).

## When implementing (future)

- Java 21, Maven, Flyway, `ddl-auto=validate`.
- **springdoc-openapi-starter-webmvc-ui** (2.8.x for Boot 3) — Swagger UI at `/swagger-ui.html`, JSON at `/v3/api-docs`. Not springdoc 3.x (Boot 4).
- Comments: only non-obvious why. See [docs/trd/comments.md](docs/trd/comments.md), [docs/trd/code-style.md](docs/trd/code-style.md), and [docs/trd/openapi.md](docs/trd/openapi.md).
- JPA for CRUD; native SQL/`JdbcTemplate` for SKIP LOCKED, Reminder/no-show clock SQL, outbox snapshot.
- Record DTOs; explicit JPA entities.
- Testcontainers: Postgres, RabbitMQ, Redis. Rate limits off in tests.
- Prove “never the same Reminder twice” with a concurrent-worker test.

## Commands (after code exists)

```bash
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

Swagger: `/swagger-ui.html`. Health: `/actuator/health`.
