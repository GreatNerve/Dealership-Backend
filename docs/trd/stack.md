# Stack

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| App | Spring Boot 3.x (Maven) |
| API | Spring MVC, Bean Validation, **springdoc-openapi-starter-webmvc-ui 2.8.x** (Boot 3 — not springdoc 3.x). UI `/swagger-ui.html`, JSON `/v3/api-docs`. See [openapi.md](openapi.md). |
| Auth | Spring Security JWT (**7 days**, `APP_JWT_TTL`, no refresh); `CUSTOMER`, `DEALERSHIP_STAFF`; `dev` may skip auth |
| CORS | All origins (`APP_CORS_ORIGINS=*`) |
| DB | PostgreSQL 16 |
| Migrations | Flyway; `spring.jpa.hibernate.ddl-auto=validate` |
| ORM | JPA for HTTP CRUD and Notification/outbox rows; native SQL / `JdbcTemplate` for clock and SKIP LOCKED claim |
| Time | `timestamptz` + `display_offset`. Due math is SQL `interval`. Lean mail snapshot. See [time.md](time.md). |
| IDs | UUID primary keys |
| Broker | RabbitMQ, Spring AMQP |
| Cache | Redis (Lettuce) for Bucket4j only |
| Rate limit | Bucket4j token bucket + Redis |
| Mail | Stub default; Brevo SMTP; `notify: false` appends `logs/notifications.log`; **2–4** workers (default 2); 30s lease + heartbeat |
| Reminders | Config `app.reminders.offsets` Duration list (`APP_REMINDER_OFFSETS`, default `24h,2h`) and `app.reminders.no-show-grace`. Due times via SQL `offset_minutes * interval '1 minute'`. |
| Enums | PostgreSQL `ENUM` types + Java enums for **closed** statuses/roles/`ApiErrorCode`. Reminder offsets are config minutes, not an enum. |
| Format | Spotless + Google Java Format. `./mvnw spotless:apply`. See [code-style.md](code-style.md). |
| Observability | Actuator, Micrometer, structured logs (no raw contact / Vehicle Number) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (Postgres, RabbitMQ, Redis) |
| Style | No Lombok. Records for DTOs. Explicit JPA entity classes. **Reuse, no copy-paste.** Comments only non-obvious why. See [comments.md](comments.md), [code-style.md](code-style.md). |

Packages (in-process modules): `identity`, `dealership`, `customer`, `vehicle`, `appointment`, `reminder`, `notification`.
