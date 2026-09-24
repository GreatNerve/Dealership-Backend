# Test harness

## Recording stub

Implement `NotificationSender` for tests that stores an in-memory list:

- offset minutes
- appointment id
- idempotency key
- timestamp
- whether it threw (for failure tests)
- correlation id (`notifications.id`)

Clear per test. Never log full contact.

Webhook tests use a **stub adapter** (JSON with Correlation Key + event type), not live Brevo. `APP_DELIVERY_WEBHOOK_SECRET` is a test constant.

## Testcontainers

Default integration: Postgres 16 (same family as prod), Flyway.

E2E: Postgres + RabbitMQ + Redis.

Pin images (not `latest`). `@ServiceConnection` / Spring Boot Testcontainers.

## Clock

A test `Clock` (or `TimeProvider`) so no-show and due-poller tests do not wait real hours. Production uses system UTC.

## Seed

Optional SQL/demo seed: 1 Dealership (`Asia/Kolkata`), 1 Staff (`staff@greatnerve.com` / `password1`), 1 Customer (`customer@greatnerve.com` / `password1`), 2 Vehicles — same as the video. E2E may create via HTTP instead; either is fine if ids are explicit. Create Appointments with `scheduledAt` including offset (e.g. `+05:30`).

## Commands (when code exists)

```bash
./mvnw test                  # unit + integration + e2e, rate limits off
./mvnw test -Dgroups=e2e     # HTTP e2e + capacity burst
```

Do not require a human Docker Compose for CI. Testcontainers is the suite. Compose is for local manual runs and the video.

Appointment (24h + 2h Reminders): `bash scripts/test-appointment.sh http://localhost:8080` or `bash scripts/test-appointment.sh https://dealership.greatnerve.com`. See [../../manual-appointment.md](../../manual-appointment.md).
