# Observability (TRD)

Log: `correlation_id` (one filter: header or new UUID, MDC), `appointment_id`, `reminder_id`, `notification_id`, `worker_id`, `attempt`. Never full contact or **Vehicle Number**.

Metrics: created, sent, retries, dead-letter, lateness (`processed_at - scheduled_at`), expired leases, outbox lag.

Health: Actuator liveness vs readiness (DB and broker).

Config env (see `.env.example`, one section per type): App, JWT, Postgres, Redis, RabbitMQ, Mail / Notification (`APP_NOTIFICATIONS_MODE`, `SPRING_MAIL_*`; SMTP key never in git), Appointment, Reminder, Workers, Idempotency, Pagination, Rate limit. No secrets or durations live only in Java.
