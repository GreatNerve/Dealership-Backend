# Code style (TRD)

**No application code until asked.** When we implement: optimized, reused, no copy-paste. Smallest code that matches the docs.

## Reuse before add

Search the repo for an existing function, query, mapper, or SQL before writing a new one. Two call sites → one shared place. A third copy is a bug.

One of each (names can differ; the *job* must not be duplicated):

| Job | Lives once |
| --- | --- |
| List `page`/`size`/`q` bind + 400 rules | shared list query |
| Page JSON `{ items, page, size, totalElements, totalPages }` | one record + one mapper |
| Own / home-Dealership / else 404 | one access check |
| Error JSON + `correlationId` | one `@ControllerAdvice` |
| Parse `scheduledAt` → Instant + `display_offset` | one time parse |
| Mail Local Wall Time from Instant + offset | one formatter (HTTP Customer GET and mail) |
| Reminder due-time `INSERT … SELECT` | one SQL, used by create **and** reschedule |
| SKIP LOCKED claim + lease heartbeat | one lease helper; Reminder and outbox pass table/SQL, not two copy-pasted workers |
| `NotificationSender` | one interface; stub and SMTP implement it; mode flag picks the bean |
| Offset list from config | one `@ConfigurationProperties`; bind `interval` from that list |

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

HTTP lists: `LIMIT`/`OFFSET` or keyset-equivalent via Spring Data pagination — never load the table then slice in Java.

## Refactor while touching

Leave code you edit cleaner: extract the duplicate, delete the dead branch, keep the diff inside the task. Do not rewrite untouched modules.

Packages: modules under `com.dealership.*`. Cross-cutting in `com.dealership.shared` (errors, page, access, time format, lease). Native SQL next to the module that owns the table (`reminder`, `notification`), not pasted into controllers.

## Easy to miss (lock now)

Not new product — implementation defaults so we do not invent them in code:

- Passwords: Spring `PasswordEncoder` (BCrypt). Never store or log raw passwords.
- Email unique **normalized** (trim + lowercase) so `A@x.com` and `a@x.com` collide.
- VIN unique **normalized** (trim + uppercase). Search/logs still never use the full VIN.
- `@Transactional` on Appointment create/reschedule/cancel (Appointment + Reminders + idempotency).
- Correlation id: request header or new UUID; MDC; error body. One filter.
- Bean Validation on records; one 400 shape.
- Flyway owns schema; no Hibernate `update`.
- Config via `@ConfigurationProperties`, not scattered `env.get`.
- Tests reuse Testcontainers / recording stub from [../testing/harness.md](../testing/harness.md); do not spin a second stub.

## Comments

[comments.md](comments.md) — why only, never what.
