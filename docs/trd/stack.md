# Stack

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| App | Spring Boot 3.x (Maven) |
| API | Spring MVC, Bean Validation, **springdoc-openapi-starter-webmvc-ui 2.8.x** (Boot 3 — not springdoc 3.x). UI `/swagger-ui.html`, JSON `/v3/api-docs`. See [openapi.md](openapi.md). |
| Auth | Spring Security JWT (**1 day**, no refresh); `CUSTOMER`, `DEALERSHIP_STAFF`; `dev` may skip auth |
| DB | PostgreSQL 16 |
| Migrations | Flyway; `spring.jpa.hibernate.ddl-auto=validate` |
| ORM | JPA for HTTP CRUD; native SQL / `JdbcTemplate` for clock, SKIP LOCKED, outbox snapshot |
| Time | `timestamptz` + `display_offset`. Due math is SQL `interval`. Lean mail snapshot. See [time.md](time.md). |
| IDs | UUID primary keys |
| Broker | RabbitMQ, Spring AMQP |
| Cache | Redis (Lettuce) for Bucket4j only |
| Rate limit | Bucket4j token bucket + Redis |
| Mail | Stub default; Brevo SMTP / Mailhog; **2–4** workers (default 2); 30s lease + heartbeat |
| Reminders | `app.reminders.offsets` default `24h,2h` — not hardcoded in Java. Due times via SQL `interval`. |
| Observability | Actuator, Micrometer, structured logs (no raw contact/VIN) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (Postgres, RabbitMQ, Redis) |
| Style | No Lombok. Records for DTOs. Explicit JPA entity classes. **Reuse, no copy-paste.** Comments only non-obvious why. See [comments.md](comments.md), [code-style.md](code-style.md). |

Packages (in-process modules): `identity`, `dealership`, `customer`, `vehicle`, `appointment`, `reminder`, `notification`.
