# Notification (TRD)

Outbox publisher drains with SKIP LOCKED (`OutboxRepository.claim`) → RabbitMQ. Notification and outbox **rows** are JPA (`NotificationRepository`, `OutboxEventRepository`). **2–4** consumers (default 2), each `prefetch=1`. Lease 30s with heartbeat; SMTP timeout shorter than lease.

Send path lives in `com.dealership.notification.smtp` (`NotificationSender`, stub, SMTP, `MailWorker`). Notification rows, outbox, and HTTP stay in `com.dealership.notification`.

`NotificationSender`: `stub` logs payload (no raw contact); `smtp` uses JavaMail to **Brevo**. Body is one `ReminderMail` (HTML + plain text) from the **outbox snapshot**: local wall time from `scheduled_at` + `display_offset` (`10:00 PM`, no UTC in the mail), Vehicle make/model/year + **Vehicle Number**, shop name. Subject `Service appointment — {dealership}` — not “2-hour reminder”. From `APP_MAIL_FROM` (`dheeraj@greatnerve.com`). `notify: false` does not use that sender — `FileNotificationLog` appends `app.notifications.log-dir` / `notifications.log` (`APP_NOTIFICATIONS_LOG_DIR`, default `logs`). Never send UTC as the only time. Never log full **Vehicle Number**. Never use the EC2 host zone. Do not reload Appointment/Customer/Vehicle/Dealership entities to build the body. See [time.md](time.md).

Notification idempotency key: `appointmentId:offsetMinutes:scheduleVersion` (unique).

Transient (SMTP auth, timeout, 429, 5xx) → `RETRY_SCHEDULED`, exponential backoff + jitter (30s, 60s, 2 min, 4 min, cap 5 minutes), max 5 attempts. Invalid contact → `DEAD_LETTER` immediately.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/appointments/{id}/reminders` | Staff, home Dealership, else 404. One nested `notification` **per Reminder**, always present. Also `offsetMinutes` + `dueAt` (UTC Instant when that mail should send). Client formats with Dealership Timezone. No `dueAtLocal`. No `notifications` row → `{ "id": null, "status": "NOT_SCHEDULED", … }`. Do not insert that row. `lastError` is Staff-only. Not a list GET; do not paginate. |
| POST | `/notifications/{id}/replay` | Dead-letter. Same key. 202. 409 if already SENT. Needs a real Notification id (`NOT_SCHEDULED` has none). |

Staff JWT (or `dev`). Config: `app.notifications.mode=stub|smtp` (`APP_NOTIFICATIONS_MODE`), `app.notifications.log-dir` (`APP_NOTIFICATIONS_LOG_DIR`, default `logs`). SMTP (Brevo): `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_AUTH`, `SPRING_MAIL_STARTTLS`. Password stays in local `.env` only. `notify: true` uses this sender; `notify: false` still writes `logs/notifications.log`. Java `NotificationStatus` includes `NOT_SCHEDULED` for this GET. PostgreSQL `notification_status` does **not** — GET synthesizes it. Stored rows stay `PENDING`…`CANCELLED`.

How to read the pair:

Example (Staff, shop `Asia/Kolkata`; visit `2026-09-22T22:00:00+05:30`; 24h offset):

```json
{
  "offsetMinutes": 1440,
  "dueAt": "2026-09-21T16:30:00Z",
  "reminderStatus": "PENDING",
  "notification": {
    "id": null,
    "status": "NOT_SCHEDULED",
    "attempts": 0,
    "lastError": null,
    "sentAt": null,
    "nextAttemptAt": null
  }
}
```

Client: format `dueAt` with Dealership `timezone` (`Asia/Kolkata`), not `Date` in the browser zone.

| What you see | Meaning |
| --- | --- |
| Reminder `PENDING`, Notification `NOT_SCHEDULED` | Window not due yet. |
| Reminder `EXPIRED` / `CANCELLED`, Notification `NOT_SCHEDULED` | Window skipped or Appointment moved. Not a SMTP failure. |
| Notification `SENT` + `sentAt` | Delivered (file log, stub, or SMTP). Cannot unsend. |
| Notification `RETRY_SCHEDULED` + `lastError` | Transient failure; will retry. |
| Notification `DEAD_LETTER` + `lastError` | Permanent or max attempts. Staff `POST /notifications/{id}/replay`. |
