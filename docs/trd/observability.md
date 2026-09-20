# Observability (TRD)

Log: `correlation_id` (one filter: header or new UUID, MDC), `appointment_id`, `reminder_id`, `notification_id`, `worker_id`, `attempt`. Never full contact or VIN.

Metrics: created, sent, retries, dead-letter, lateness (`processed_at - scheduled_at`), expired leases, outbox lag.

Health: Actuator liveness vs readiness (DB and broker).

Config env: datasource, RabbitMQ, Redis, `app.notifications.mode`, SMTP, JWT secret, `bucket4j.enabled`, `app.reminders.offsets` (default `24h,2h`), `app.workers.concurrency` (default 2, max 4).
