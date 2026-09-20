# Test harness

## Recording stub

Implement `NotificationSender` for tests that stores an in-memory list:

- offset minutes
- appointment id
- idempotency key
- timestamp
- whether it threw (for failure tests)

Clear per test. Never log full contact.

## Testcontainers

Default integration: Postgres 16 (same family as prod), Flyway.

E2E: Postgres + RabbitMQ + Redis.

Pin images (not `latest`). `@ServiceConnection` / Spring Boot Testcontainers.

## Clock

A test `Clock` (or `TimeProvider`) so no-show and due-poller tests do not wait real hours. Production uses system UTC.

## Seed

Optional SQL/demo seed: 1 Dealership (`Asia/Kolkata`), 1 Staff, 1 Customer, 2 Vehicles — same as the video. E2E may create via HTTP instead; either is fine if ids are explicit. Create Appointments with `scheduledAt` including offset (e.g. `+05:30`).

## Commands (when code exists)

```bash
./mvnw test                  # unit + integration + e2e, rate limits off
./mvnw test -Dgroups=e2e     # HTTP e2e + capacity burst
```

Do not require a human Docker Compose for CI. Testcontainers is the suite. Compose is for local manual runs and the video.

Localhost Appointment (24h + 2h Reminders): `bash scripts/test-appointment.sh` or [../../manual-appointment.md](../../manual-appointment.md).
