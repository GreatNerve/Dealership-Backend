# Email tracking (TRD)

Product: [../prd/email-tracking.md](../prd/email-tracking.md). Send pipeline: [notification.md](notification.md).

Java: `com.dealership.notification.webhook` (`DeliveryWebhookAdapter`, Brevo + stub). Ledger: `notification_delivery_events` (JPA). HTTP GETs stay on `NotificationService` (list flags + item timeline).

## Contract

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/webhooks/delivery/{provider}` | Public. `APP_DELIVERY_WEBHOOK_SECRET` in `Authorization` (`Bearer …`, `Token …`, or the raw secret), compared constant-time. `{provider}` selects the adapter (`brevo`, `stub`). JSON **array** = each element (max **100**, else `400 VALIDATION_ERROR`); object = one event. Map payload → generic `DeliveryEventType`. Resolve Notification by Correlation Key (UUID). One transaction: load known ids, insert new rows. `provider_event_id` over 255 → SHA-256 hex (`Inputs.fit`); `raw_type` clip 64. Unique `(notification_id, provider, provider_event_id)` → skip duplicate (200). Unknown Notification → 204 no insert (2xx so the provider stops retrying). Unmapped type → 204. **Do not** change worker `Notification.status`. Do not log recipient email. **Not rate-limited** (provider bursts). |
| GET | `/notifications` | Staff. Derived `opened` / `bounced` = `EXISTS` on events. `hasEvent` filters by type. `appointmentId` items include `events` (same order as item GET). |
| GET | `/notifications/{id}` | Staff. `events`: `eventType`, `occurredAt`, `provider`, **latest `occurredAt` first** (then insert time). |
| GET | `/notifications/stats` and `/dashboard/stats` | `opened` / bounce counts from events in `[from, to)`. `bounced` includes `SOFT_BOUNCE`, `HARD_BOUNCE`, and `BLOCKED`. Heal `occurred_at` epoch ≥ 1e12 as millis; CTE still pulls those unhealed rows. Bounce/open `buckets[]` use first event in range per Notification. Dashboard omit `bucket` = totals only. |

Staff JWT on the GETs. Webhook is the secret, not JWT.

Config: `APP_DELIVERY_WEBHOOK_SECRET` (required outside `dev`/`test`), `APP_NOTIFICATIONS_CORRELATION_HEADER` (default `X-Mailin-custom`).

Brevo `ts_epoch` ≥ 1e12 is milliseconds; smaller values are seconds. Storing millis as seconds is year ~58699 — heal on ingest, GET, stats, and Flyway V912.

## What tracking is not

- Not SMTP 250 (that is Notification `SENT`).
- Not Reminder status.
- Not a snapshot column on `notifications`.
- Not Kafka, not polling Brevo.

## How to read a row

| What you see | Meaning |
| --- | --- |
| `SENT` and no events | Worker delivered. Customer has not opened (or webhook not in yet). |
| `SENT` + event `OPENED` | Opened. Status stays `SENT`. |
| `SENT` + `SOFT_BOUNCE` / `HARD_BOUNCE` / `BLOCKED` | Bounce after send (provider reject). Status stays `SENT`. |
| `DEAD_LETTER` | Worker never got SMTP 250. No tracking until a send succeeds (or replay). |
