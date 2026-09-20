# Notification (TRD)

Outbox publisher drains with SKIP LOCKED → RabbitMQ. **2–4** consumers (default 2), each `prefetch=1`. Lease 30s with heartbeat; SMTP timeout shorter than lease.

`NotificationSender`: `stub` logs payload (no raw contact); `smtp` uses JavaMail to Mailhog or Brevo. Format from the **outbox snapshot** (`scheduled_at` + `display_offset` → `10:00 PM (UTC+05:30)`). Never send UTC as the only time. Never use the EC2 host zone. Do not reload Appointment/Customer/Vehicle/Dealership entities to build the body. See [time.md](time.md).

Notification idempotency key: `appointmentId:reminderType:scheduleVersion` (unique).

Transient (timeout, 429, 5xx) → `RETRY_SCHEDULED`, exponential backoff + jitter, max 5 attempts. Permanent → `DEAD_LETTER` in Postgres (source of truth).

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/notifications/{id}/replay` | Dead-letter or notify-off. Same key. 202. 409 if already SENT. |

Staff JWT (or `dev`). Config: `app.notifications.mode=stub|smtp`.
