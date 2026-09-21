# Code style (TRD)

Optimized, reused, no copy-paste. Smallest code that matches the docs.

## Reuse before add

Search the repo for an existing function, query, mapper, or SQL before writing a new one. Two call sites → one shared place. A third copy is a bug.

One of each (names can differ; the *job* must not be duplicated):

| Job | Lives once |
| --- | --- |
| List `page`/`size`/`q` bind + 400 rules | shared list query |
| Page JSON `{ items, page, size, totalElements, totalPages }` | one record + one mapper |
| Own / home-Dealership / else 404 | one access check |
| Error JSON + `correlationId` | one `ApiResponse` envelope (`success`, `data`, `error`, `message`, `correlationId`) |
| Parse `scheduledAt` → Instant + `display_offset` | one time parse |
| Mail Local Wall Time from Instant + offset | one formatter (HTTP Customer GET and mail) |
| Reminder mail subject / text / HTML | one `ReminderMail` from the outbox snapshot |
| Reminder due-time `INSERT … SELECT` | one SQL, used by create **and** reschedule |
| SKIP LOCKED claim + lease heartbeat | one lease helper; Reminder and outbox pass table/SQL, not two copy-pasted workers |
| `NotificationSender` | one interface; stub and SMTP implement it; mode flag picks the bean. `notify: false` uses `FileNotificationLog` (`logs/notifications.log`), not this interface |
| Offset list from config | one `@ConfigurationProperties`; bind `List<Duration>` (`APP_REMINDER_OFFSETS`) |
| Sanitize / normalize strings | `Inputs` (trim + strip controls). JSON deserializer and query/form/header binder both call it. `Inputs.email` lowercases. |

Do **not** invent a generic “worker framework” for two queues. Same pattern, not a new abstraction layer.

## No redundancy

- No second pagination implementation per controller.
- No Java `Instant.minus` that repeats SQL due math.
- No Customer GET vs mail each formatting time differently.
- No Lombok *and* records. Records for DTOs; explicit entities.
- No unused fields, unused mappers, commented-out code we just wrote.
- Map only columns the caller needs (lean projection / outbox snapshot). Do not `JOIN FETCH` the full graph for mail.

## Optimize (already locked)

Clock, expire, no-show, claim `WHERE` = set-based SQL. Partial indexes. Bounded SKIP LOCKED. Outbox snapshot so the consumer does not reload entities. See [time.md](time.md).

HTTP lists: Spring Data pagination on JPA entities (derived query or Specification). Customer lists bind `customerId` from the token into `WHERE` / ownership `JOIN`. Nested refs on a page are **one `findAllById` per table**, never `findById` inside the row map. Native SQL stays SKIP LOCKED claim and Reminder/no-show clock — not Notification row updates, not Customer/Vehicle list search.

## Refactor while touching

Leave code you edit cleaner: extract the duplicate, delete the dead branch, keep the diff inside the task. Do not rewrite untouched modules.

Packages: modules under `com.dealership.*`. Cross-cutting in `com.dealership.shared` (errors, page, access, time format, lease). Native SQL stays with the owning module. Status and role columns are Java enums mapped to PostgreSQL ENUM types.

## What lives where

Yes — **database access belongs in the Repository.** Not in the Controller. Not in the DTO.

| Kind | Holds | Example | Does not hold |
| --- | --- | --- | --- |
| **Controller** | HTTP: path, status, auth, bind `page`/`size`/`q`, call service | `AppointmentController` | SQL, “one Confirmed per Vehicle”, password hashing |
| **Service** | Use cases in one `@Transactional` (book, login, due poll, replay) | `AppointmentService`, `ReminderService` | `HttpServletRequest`, `ResultSet`, JSON field names |
| **Repository** | Load/save/query. JPA CRUD; `JdbcTemplate` only for SKIP LOCKED / Reminder clock | `AppointmentRepository`, `ReminderRepository`, `NotificationRepository` | Auth rules, HTTP DTOs |
| **Entity** | One table row | `AppointmentEntity` | Request bodies, `PageResponse` |
| **DTO / records** | API JSON in and out | `CreateAppointmentRequest` | `@Entity`, `EntityManager` |
| **Mapper** | Entity ↔ DTO when a second caller needs the same map | (inline `toResponse` until then) | SQL, HTTP |
| **Exception** | `ApiErrorCode` + `GlobalExceptionHandler` | `GlobalExceptionHandler` | Business policies |
| **Config** | Spring beans, security, OpenAPI, `@ConfigurationProperties` | `SecurityConfig` | Use cases |
| **Scheduler** | `@Scheduled` tick only; calls a service | `ReminderScheduler`, `OutboxPublisher` | SQL, SMTP |
| **Worker** | Background consume / execute | `MailWorker` | HTTP |
| **Sender** | External mail provider | `NotificationSender` | Postgres |
| **Common** | Shared across modules | `TimeProvider`, `PageQueries` | Feature policies |

Packages stay **by module** (`com.dealership.appointment`, `…notification`), not `controller/` / `service/` subfolders. Nested folder only for a **concern** (mail send lives in `notification.smtp`). File *role* matches the table; folder matches the feature. Reminder has no JPA entity: clock and SKIP LOCKED are set-based SQL ([ADR 0009](../adr/0009-jpa-plus-native-skip-locked.md)). `JdbcTemplate` in `ReminderRepository` is still a Repository.

Thin list/get (Dealership list, Customer directory) may call the Repository from the Controller. The query still lives on the Repository.

Spring Boot stereotypes are web / service / persistence. Scheduler, worker, sender, DTO, mapper are our roles on top. Official pages:

- Stereotypes (presentation / service / persistence): [Classpath scanning](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)
- `@RestController` is `@Controller` + `@ResponseBody`: [RestController](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RestController.html)
- `@Service` as business facade: [Service](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/stereotype/Service.html)
- Transactions on the use-case (`@Transactional`): [Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- Queries live on the repository interface (not the entity): [JPA @Query](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- JPA Specification / Criteria: [Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)

## Formatter

Spotless + Google Java Format (Java 21). Indent 2 spaces.

```bash
./mvnw spotless:apply
./mvnw spotless:check
make fmt
```

`spotless:check` runs on `verify`. VS Code / Cursor: format on save via `.editorconfig` and the Google Java Format settings in `.vscode/settings.json`.

## Easy to miss (lock now)

Not new product — implementation defaults so we do not invent them in code:

- Passwords: Spring `PasswordEncoder` (BCrypt). Never store or log raw passwords.
- Email unique **normalized** (`Inputs.email`: sanitize + lowercase) so `A@x.com` and `a@x.com` collide.
- **Vehicle Number** unique **normalized** (`Inputs.sanitize`, then uppercase, strip spaces/hyphens). Search/logs still never use the full plate.
- `@Transactional` on Appointment create/reschedule/cancel (Appointment + Reminders + idempotency).
- Correlation id: request header or new UUID; MDC; error body. One filter.
- Bean Validation on records; one 400 shape.
- Flyway owns schema; no Hibernate `update`.
- Config via `@ConfigurationProperties`, not scattered `env.get`.
- Tests reuse Testcontainers / recording stub from [../testing/harness.md](../testing/harness.md); do not spin a second stub.

## Comments

[comments.md](comments.md) — why only, never what.
