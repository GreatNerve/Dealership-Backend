# AGENTS.md

Dealership vehicle-service **Appointment** booking and **Reminder** delivery. Modular Spring Boot monolith. **Docs are the source of truth.** Discussion still updates docs first; application code exists under `src/` after the explicit implement request.

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
- Reminder offsets from config (`APP_REMINDER_OFFSETS`, default `24h,2h`). Not a Java enum of types. No confirmation mail in v1.
- Mail workers 2–4 (default 2), 30s lease with heartbeat, SMTP timeout < lease. Due Reminders and outbox drain a **Claim Batch** from CPU count (`APP_WORKERS_CLAIM_BATCH=0` auto, floor 18 for **500k Appointments/day** = 10× the assignment 50k, max 50). Hikari and Tomcat also auto from CPUs unless pinned. Not a “poll 10×”. Why: [docs/decision/scale.md](docs/decision/scale.md).
- Redis is Bucket4j HTTP limits only, never uniqueness or mail throttle. Limits are **per endpoint** (method + path, UUID collapsed to `{id}`), **15 / 60s**, never a window longer than 60s. Login and register do not share tokens. Delivery webhooks are not limited.
- Reminder uniqueness and one Confirmed Appointment per Vehicle are **PostgreSQL**. The Vehicle cap is `APP_ONE_CONFIRMED_PER_VEHICLE` (default `true`). `false` stores `one_confirmed=false` so the unique index does not apply.
- Staff book **home Dealership only**.
- List GETs are paginated and searchable (`q`). Default page `size` is **100**, max **1000** (`APP_PAGE_DEFAULT_SIZE` / `APP_PAGE_MAX_SIZE`). Nested refs on a page are one `findAllById` (or `findByCustomerIdIn`) per table, not `findById` per row. Customer/Staff Appointment reads: own or home shop, else 404. Optional Instant `from`/`to` on `GET /appointments` (`scheduled_at`; frontend Dealership-local “today”). Staff search `GET /customers` (and that Customer’s Vehicles) to obtain ids for booking — home Dealership membership required, else 404. Customer GET/list includes optional `name`. Staff may `POST /customers` and `POST /customers/{id}/vehicles` for walk-ins, then book home Dealership only.
- Staff `GET /appointments/{id}/reminders` (home Dealership): **all Schedule Versions** (not current-only). Each item has `scheduleVersion`, `offsetMinutes`, `dueAt` (UTC send Instant), and a `notification` object. No row yet → `NOT_SCHEDULED` (not JSON `null`). `lastError` when a row exists. Replay still `POST /notifications/{id}/replay` at that home Dealership only. Replay of a **System** Notification reopens the Reminder to `PROCESSING` with a live lease so `MailWorker` can send. **Manual** replay has no Reminder.
- Staff `GET /notifications` is the shop-wide list (home Dealership via `notifications.dealership_id`): paginated and searchable (`q` is customer name / Vehicle Number), Instant `from`/`to` (`from` strictly before `to`; frontend computes Dealership-local “today”), `status` (not `NOT_SCHEDULED`), `generation`, `channel`, `appointmentId`, optional `hasEvent`. Nested Appointment (customer, Vehicle, visit time) on list and item GET. `GET /notifications/{id}` includes the **Delivery Event** timeline. `GET /dashboard/stats?from&to&bucket=` is one Staff call (Appointment + Notification totals and zero-filled `buckets[]` for `DAY`/`WEEK`/`MONTH` in shop TZ, max 400 slices). Resource `GET /notifications/stats` and `GET /appointments/stats` stay for totals (optional `bucket`). `POST /appointments/{id}/notifications` is **Manual** send (`subject` + `body`, `Idempotency-Key` required); templates live in the frontend. Customer does not list Notifications.
- **Channel** is `EMAIL` in v1. **Generation** is `SYSTEM` (Reminder due) or `MANUAL` (Staff compose, `reminder_id` null). **Delivery Events** are append-only; they do not mutate worker `Notification.status`. Provider webhooks are `POST /webhooks/delivery/{provider}` with `APP_DELIVERY_WEBHOOK_SECRET` (`Authorization` Bearer, Token, or the raw secret; constant-time compare), not User JWT. A JSON array is accepted (max 100). Unknown Notification → 204. Correlation is the Notification UUID in a provider-mapped SMTP header (`APP_NOTIFICATIONS_CORRELATION_HEADER`, Brevo default `X-Mailin-custom`). Schema/API never use Brevo field names. Manual send uses a Notification lease and `NotificationScheduler` retries; System uses the Reminder lease.
- Reminder offsets, no-show grace, JWT, workers, pagination, rate limits, mail, and the one-Confirmed-per-Vehicle cap come from **config/env**, not Java literals. Closed statuses/roles/`ApiErrorCode` are PostgreSQL + Java enums, not `varchar`. Reminder offsets are a Duration list stored as `offset_minutes`.
- Format Java with Spotless (`./mvnw spotless:apply`).
- Shop-floor **In Progress** is later. **Completed** is v1. Staff at home Dealership complete, cancel, and reschedule Confirmed visits. Customers cancel and reschedule their own (`POST /appointments/{id}/cancel|reschedule`). Customer complete → 403; other shop / other customer → 404.
- JWT access token 7 days. `APP_JWT_SECRET` must be at least 32 bytes (start refuses to pad). Non-`dev`/`test` refuses the committed default secret. CORS allows all origins (`APP_CORS_ORIGINS=*`). Idempotency-Key TTL 24h, unique per User. Expired `idempotency_keys` purged at UTC midnight. Notification `idempotency_key` is not TTL-purged.
- `make run` defaults `SPRING_PROFILES_ACTIVE=dev` (demo seed, local Vehicle cap). Production must omit that profile so V900 seed and the local cap do not run.
- Do not log raw contact, User name, or full **Vehicle Number**. Default Notification Mode is stub. `notify: false` appends `logs/notifications.log` (ids + wall time); `notify: true` uses stub or SMTP.
- Store `scheduled_at` UTC and `display_offset` from `scheduledAt`. No Customer timezone field. Mail = Booking Offset. Staff GET = Dealership Timezone. JVM UTC (EC2 region irrelevant).
- Clock math is PostgreSQL (set-based `interval`, claim `WHERE`, no-show `UPDATE`). Do not load full graphs to subtract hours. Outbox carries a lean snapshot for mail.
- Comments in Java: only non-obvious why. Swagger via springdoc 2.8.x (Boot 3), not 3.x. Authorize is OAuth2 password (username = email) like FastAPI, not paste-only Bearer.
- Reuse: one implementation per job (page, 404, time format, Instant `from`/`to`, `StatsBucket`, Reminder SQL, sender, `DeliveryWebhookAdapter`, `ApiErrorCode`, `Inputs` sanitize). No copy-paste. See [docs/trd/code-style.md](docs/trd/code-style.md).

## Implementation

- Java 21, Maven, Flyway, `ddl-auto=validate`.
- **springdoc-openapi-starter-webmvc-ui** (2.8.x for Boot 3) — Swagger UI at `/swagger-ui.html`, JSON at `/v3/api-docs`. Authorize is OAuth2 password (username = email). Not springdoc 3.x (Boot 4).
- Comments: only non-obvious why. See [docs/trd/comments.md](docs/trd/comments.md), [docs/trd/code-style.md](docs/trd/code-style.md), and [docs/trd/openapi.md](docs/trd/openapi.md).
- JPA for CRUD; native SQL/`JdbcTemplate` for SKIP LOCKED and Reminder/no-show clock SQL.
- Record DTOs; explicit JPA entities.
- Testcontainers: Postgres, RabbitMQ, Redis. Rate limits off in tests.
- Prove “never the same Reminder twice” with a concurrent-worker test.
- HTTP e2e and the capacity burst live in `src/test/java/com/dealership/e2e/` (`./mvnw test`). Not a script outside the repo.

## Commands (after code exists)

```bash
make hooks          # once: install git pre-commit (Spotless + tests)
make deps           # docker compose -f docker/docker-compose.deps.yml up -d
make fmt            # ./mvnw spotless:apply
make test           # format then ./mvnw test
make run            # deps + ./mvnw spring-boot:run
make app            # deps + app image (docker/docker-compose.app.yml)
make appointment    # localhost 24h + 2h Appointment (scripts/test-appointment.sh)
make stop           # kill whatever is on PORT (default 8080)
make up             # docker compose up -d --build (root includes deps + app)
```

Without make: `docker compose -f docker/docker-compose.deps.yml up -d`, `./mvnw spotless:apply`, `./mvnw test`, `./mvnw spring-boot:run`.

Pre-commit (`.githooks/pre-commit`, installed by `make hooks`): on commits that touch `src/` or `pom.xml`, runs Spotless then `./mvnw test`. No Python. Docs-only commits skip tests. `git commit --no-verify` bypasses it.

Swagger: `/swagger-ui.html`. Health: `/actuator/health` (public). Prometheus scrape: `/actuator/prometheus` (JWT). Production host: `https://dealership.greatnerve.com`.
