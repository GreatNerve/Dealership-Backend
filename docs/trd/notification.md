# Notification (TRD)

Outbox publisher drains with SKIP LOCKED (`OutboxRepository.claim`, same **Claim Batch** as Reminders, auto from CPUs) → RabbitMQ. Claim includes `PROCESSING` rows whose lease has expired (same reclaim as Reminders). Notification, outbox, and **Delivery Event** rows are JPA. **2–8** consumers (default 2), each `prefetch=1`. Lease 30s with heartbeat; SMTP timeout shorter than lease. `markSent` / `markDead` / `markRetry` no-op unless the row is still `PROCESSING` with a live lease (**System**: Reminder lease; **Manual**: Notification lease — same helper).

Send path lives in `com.dealership.notification.smtp` (`NotificationSender`, stub, SMTP, `MailWorker`). Webhook adapters live in `com.dealership.notification.webhook`. Notification rows, outbox, HTTP, and events stay in `com.dealership.notification`. Breaking schema recreate is allowed (demo wipe); do not dual-write old and new shapes.

`NotificationSender`: `stub` logs payload (no raw contact); `smtp` uses JavaMail to **Brevo**. The sender interface takes `correlationId` + a headers map; it does not mention Brevo. `MailWorker` fills that map from `APP_NOTIFICATIONS_CORRELATION_HEADER` (default `X-Mailin-custom`) whose value is `notifications.id` (Correlation Key). **System** body is one `ReminderMail` (HTML + plain text) from the **outbox snapshot**: local wall time from `scheduled_at` + `display_offset` (`10:00 PM`, no UTC in the mail), `Hi {name},` when `users.name` is set, Vehicle make/model/year + **Vehicle Number**, shop name. Subject `Service appointment — {dealership}` — not “2-hour reminder”. **Manual** body is stored `subject`/`body` (newlines kept). SMTP wraps it in the same `ReminderMail` HTML shell as System. From `APP_MAIL_FROM` (`dheeraj@greatnerve.com`). `notify: false` does not use that sender on the **System** due path — `FileNotificationLog` appends `app.notifications.log-dir` / `notifications.log` (`APP_NOTIFICATIONS_LOG_DIR`, default `logs`). **Manual** always uses Notification Mode. Never send UTC as the only time. Never log full **Vehicle Number**. Never use the EC2 host zone. Do not reload Appointment/Customer/Vehicle/Dealership entities to build the body. See [time.md](time.md).

Notification idempotency key unique:

- **System:** `appointmentId:offsetMinutes:scheduleVersion`
- **Manual:** `appointmentId:MANUAL:notificationId`

`outbox_event_type`: `REMINDER_DUE` (system claim) and `MANUAL_NOTIFICATION` (staff compose). Same publisher and `MailWorker`.

Transient (timeout, SMTP 4xx including `421`/`450`/`451`/`452` provider rate limit) → `RETRY_SCHEDULED`, exponential backoff + jitter (2 min, 4 min, 8 min, cap 10 minutes), max 5 attempts. Expired `PROCESSING` is not reclaimed until the lease has been dead for that same 2 minute floor (webhook window). **System** retries when the Reminder poller re-claims (`next_attempt_at`). **Manual** retries when `NotificationScheduler` claims due Manual rows (`RETRY_SCHEDULED` / expired `PROCESSING` lease, SKIP LOCKED) and writes a new `MANUAL_NOTIFICATION` outbox with live `attempts` on the snapshot. SMTP auth, invalid contact (`AddressException` / `MailParseException`), and a 5xx `SendFailedException` → `DEAD_LETTER` immediately (no SMTP retries).

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/appointments/{id}/reminders` | Staff, home Dealership, else 404. **All Schedule Versions** (not current-only). Each item: `scheduleVersion`, `offsetMinutes`, `dueAt` (UTC Instant), Reminder status, nested `notification` **always** present. Order `scheduleVersion DESC`, `offsetMinutes DESC`. Client formats `dueAt` with Dealership Timezone. No `dueAtLocal`. No `notifications` row → `{ "id": null, "status": "NOT_SCHEDULED", … }`. Do not insert that row. `lastError` is Staff-only. Not a list GET; do not paginate. |
| GET | `/notifications` | Staff, home Dealership (`notifications.dealership_id`), else empty page not other shops’ rows. Paginated + `q`. Query: Instant `from`/`to` (inclusive/exclusive on `created_at`; frontend computes Dealership-local “today”; `from` must be **strictly before** `to`), `status` (worker enum, not `NOT_SCHEDULED` — that value is 400), `generation`, `channel`, `appointmentId`, `hasEvent` (`OPENED` \| `DELIVERED` \| `SOFT_BOUNCE` \| `HARD_BOUNCE` \| `CLICKED` \| `SPAM` \| `BLOCKED` \| `ERROR` \| `ACCEPTED`). Derived open/bounce = `EXISTS` on events, not columns (list `opened` is also true when the included timeline has `OPENED`). Nested **Appointment** (customer name, Vehicle Number, visit time) is batched `findAllById` after the page — not the Appointment UUID as the only UI field. Subject/body omitted on the list. When `appointmentId` is set, each item includes the same `events` timeline as the item GET so Appointment detail can badge Opened without a second round-trip. |
| GET | `/notifications/stats` | Staff, home Dealership. Query Instant `from`/`to` required. Optional `bucket` (`DAY` \| `WEEK` \| `MONTH`). JSON: `appointments` (count with `scheduled_at` in range at that shop), `notificationsSent` (`sent_at` in range), `failed` (`DEAD_LETTER` by `updated_at`), `opened` / `softBounce` / `hardBounce` / `bounced` (`EXISTS` distinct Notification with that event type and `occurred_at` in range; `bounced` is `SOFT_BOUNCE`, `HARD_BOUNCE`, or `BLOCKED`), `buckets[]` (zero-filled `date` / `sent` / `failed` / `bounced` / `opened` when `bucket` is set). Bounce/open buckets use the **first** `occurred_at` in range per Notification so summing days equals the distinct total. Stats SQL heals `occurred_at` epoch ≥ 1e12 as milliseconds and filters that healed Instant to `[from, to)` in the CTE (still includes unhealed millis rows via the epoch predicate so year-range Opened is not 0). Register this mapping **before** `/{id}`. |
| GET | `/notifications/{id}` | Staff, home Dealership, else 404. Worker fields + `channel`, `generation`, `dealershipId`, `appointmentId`, nested Appointment (same shape as list), optional `reminderId` / `offsetMinutes`. **Manual** includes `subject`/`body`. `events`: append-only timeline (`eventType`, `occurredAt`, `provider`), **latest `occurredAt` first** (then `created_at`). |
| POST | `/appointments/{id}/notifications` | Staff, home Dealership, else 404. `Idempotency-Key` required (same table as Appointment create; fingerprint includes Appointment id + subject + body). Body `{ "subject": "…", "body": "…" }` (required; subject sanitized; body `Inputs.multiline` so LF stays). Creates `generation=MANUAL`, `channel=EMAIL`, `dealership_id` from the Appointment, `reminder_id` null, stores subject/body, writes `MANUAL_NOTIFICATION` outbox. 202 + Notification id. Same worker pipeline. `400` blank subject/body or missing key. |
| POST | `/notifications/{id}/replay` | Dead-letter only. Same key. 202. Staff, home Dealership of that **Notification** (`notifications.dealership_id`), else 404. 409 `ALREADY_SENT` if SENT. 409 `REPLAY_NOT_DEAD_LETTER` if not `DEAD_LETTER`. **System:** reopens Reminder `DEAD_LETTER` → `PROCESSING` with a live lease and Notification → `PENDING`. **Manual:** Notification → `PENDING` + new outbox; no Reminder. Needs a real Notification id (`NOT_SCHEDULED` has none). |
| POST | `/webhooks/delivery/{provider}` | See **email tracking**: [email-tracking.md](email-tracking.md). |

Staff JWT (or `dev`) on all Notification GETs/POSTs except the webhook. Config: `app.notifications.mode=stub|smtp` (`APP_NOTIFICATIONS_MODE`), `app.notifications.log-dir` (`APP_NOTIFICATIONS_LOG_DIR`, default `logs`), `app.notifications.webhook-secret` (`APP_DELIVERY_WEBHOOK_SECRET`, required in non-`dev`/`test`; refuse start if blank), `app.notifications.correlation-header` (`APP_NOTIFICATIONS_CORRELATION_HEADER`, default `X-Mailin-custom`). SMTP (Brevo): `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_AUTH`, `SPRING_MAIL_STARTTLS`. Password stays in local `.env` only. Java `NotificationStatus` includes `NOT_SCHEDULED` for Reminder GET. PostgreSQL `notification_status` does **not** — GET synthesizes it. Stored rows stay `PENDING`…`CANCELLED`. Delivery events never write `OPENED` onto that enum.

How to read the Reminder pair:

Example (Staff, shop `Asia/Kolkata`; visit `2026-09-22T22:00:00+05:30`; 24h offset; current version 2 after reschedule):

```json
{
  "scheduleVersion": 2,
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
| Reminder `EXPIRED` / `CANCELLED`, Notification `NOT_SCHEDULED` | Window skipped or Appointment moved. Not a SMTP failure. Prior versions still listed. |
| Notification `SENT` + `sentAt` | Worker delivered (file log, stub, or SMTP). Cannot unsend. Opens/bounces are **events**. |
| Notification `RETRY_SCHEDULED` + `lastError` | Transient failure; will retry. |
| Notification `DEAD_LETTER` + `lastError` | Permanent or max attempts. Staff `POST /notifications/{id}/replay`. |
| Event `OPENED` on a `SENT` row | Provider reported open. Status stays `SENT`. |
| `generation: MANUAL` | Staff compose. No Reminder. Subject/body on GET by id. |

List Instant params: ISO-8601 Instants (`from`, `to`). Server does not interpret “today”. Missing both = no date filter. `from >= to` → `400`.
