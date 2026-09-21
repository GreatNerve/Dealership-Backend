# Observability (TRD)

Log: `correlation_id` (one filter: header or new UUID, MDC), `appointment_id`, `reminder_id`, `notification_id`, `worker_id`, `attempt`. Never full contact or **Vehicle Number**.

Metrics (Micrometer + `micrometer-registry-prometheus`): `dealership.appointments.created`, `dealership.reminders.claimed`, `dealership.notifications.sent` / `retry` / `dead` / `lease_skip`, `dealership.notifications.lateness` (seconds, `processed_at - reminder due`), `dealership.outbox.published`. Scrape **`GET /actuator/prometheus`** (Prometheus text, `Accept: text/plain`) or inspect **`GET /actuator/metrics`**. Both require JWT. Boot 3.5 also needs `management.prometheus.metrics.export.enabled=true` and `management.endpoint.prometheus.access=read_only` (same for `metrics`) or the scrape mapping is not registered. OpenMetrics reserves `_created`, so `dealership.appointments.created` scrapes as `dealership_appointments_total`. Health stays public: `/actuator/health` (liveness vs readiness, DB and broker) and `/actuator/info`.

HTTP security headers on every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy` (`frame-ancestors 'none'`; `'unsafe-inline'` script/style so springdoc Swagger UI still loads), `Strict-Transport-Security` on HTTPS.

Config env (see `.env.example`, one section per type): App (`APP_CORS_ORIGINS=*`; credentials off when origins include `*`), JWT, Postgres, Redis, RabbitMQ, Mail / Notification (`APP_NOTIFICATIONS_MODE`, `SPRING_MAIL_*`; SMTP key never in git), Appointment, Reminder, Workers, Idempotency, Pagination, Rate limit. No secrets or durations live only in Java.
