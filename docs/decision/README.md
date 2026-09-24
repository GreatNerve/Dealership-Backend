# Architecture decisions (from me)

This folder is **the architecture I chose and why**. Not coding style, not “EC2 later,” not JPA vs JDBC.

Front door: [../../README.md](../../README.md). Product rules: [../prd/README.md](../prd/README.md). Contracts: [../trd/README.md](../trd/README.md). Runtime picture: [../architecture.md](../architecture.md).

| Decision | File |
| --- | --- |
| Modular monolith, not microservices | [modular-monolith.md](modular-monolith.md) |
| Postgres is the clock and the ledger | [postgres-clock-and-ledger.md](postgres-clock-and-ledger.md) |
| RabbitMQ delivers due mail via outbox | [rabbitmq-outbox-delivery.md](rabbitmq-outbox-delivery.md) |
| Redis is HTTP rate limit only (token bucket) | [redis-http-rate-limit.md](redis-http-rate-limit.md) |
| SKIP LOCKED workers and leases | [skip-locked-workers.md](skip-locked-workers.md) |
| At-least-once, idempotent reminders | [at-least-once-idempotency.md](at-least-once-idempotency.md) |
| Stub/Brevo SMTP, Correlation Key, Delivery Events | [notification-pipeline.md](notification-pipeline.md) |
| Configurable offsets and send window | [send-window-and-config.md](send-window-and-config.md) |
| Dual booking, staff pinned to home shop | [dual-booking.md](dual-booking.md) |
| One Confirmed Appointment per Vehicle | [one-appointment-per-vehicle.md](one-appointment-per-vehicle.md) |
| Appointment lifecycle (no shop floor) | [appointment-lifecycle.md](appointment-lifecycle.md) |
| UTC Instant + Booking Offset from `scheduledAt` | [utc-instant-and-booking-offset.md](utc-instant-and-booking-offset.md) |
| SQL clock, lean snapshot, no app-layer time loop | [sql-clock-not-app-layer.md](sql-clock-not-app-layer.md) |
| Deps Compose vs app image | [docker-runtime.md](docker-runtime.md) |
| Size at assignment 50k × 10, pools from this machine | [scale.md](scale.md) |
