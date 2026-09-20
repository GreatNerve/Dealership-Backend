# Observability (TRD)

Log: `correlation_id` (one filter: header or new UUID, MDC), `appointment_id`, `reminder_id`, `notification_id`, `worker_id`, `attempt`. Never full contact or **Vehicle Number**.

Metrics: created, sent, retries, dead-letter, lateness (`processed_at - scheduled_at`), expired leases, outbox lag.

Health: Actuator liveness vs readiness (DB and broker).

Config env (see `.env.example`): datasource, RabbitMQ, Redis, SMTP, `APP_JWT_SECRET`, `APP_JWT_TTL`, `APP_PUBLIC_HOST`, `APP_LOCAL_HOST`, `APP_CORS_ORIGINS`, `APP_NOTIFICATIONS_MODE`, `APP_NOTIFICATIONS_LOG_DIR`, `APP_MAIL_FROM`, `APP_REMINDER_OFFSETS`, `APP_NO_SHOW_GRACE`, `APP_ONE_CONFIRMED_PER_VEHICLE`, workers, pagination, rate limits, idempotency TTL. No secrets or durations live only in Java.
