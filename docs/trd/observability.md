# Observability (TRD)

Log: `correlation_id` (one filter: header or new UUID, MDC), `appointment_id`, `reminder_id`, `notification_id`, `worker_id`, `attempt`. Never full contact or **Vehicle Number**.

Metrics (Micrometer, process-local; **not** on `/actuator/metrics` — that endpoint is not exposed): `dealership.appointments.created`, `dealership.reminders.claimed`, `dealership.notifications.sent` / `retry` / `dead` / `lease_skip`, `dealership.notifications.lateness` (seconds, `processed_at - reminder due`), `dealership.outbox.published`. Health: Actuator liveness vs readiness (DB and broker) at `/actuator/health` and `/actuator/info` only.

Config env (see `.env.example`, one section per type): App (`APP_CORS_ORIGINS=*`; credentials off when origins include `*`), JWT, Postgres, Redis, RabbitMQ, Mail / Notification (`APP_NOTIFICATIONS_MODE`, `SPRING_MAIL_*`; SMTP key never in git), Appointment, Reminder, Workers, Idempotency, Pagination, Rate limit. No secrets or durations live only in Java.
