# Email tracking (PRD)

Staff-facing name for **Delivery Events** on a **Notification**. Not a second product table. Not worker status.

Reminder = “is it time?” Notification = “did we deliver?” **Email tracking** = “what did the provider report?” (opened, bounce, click, delivered).

## Stories

1. As a Staff Member, I want to see whether a Customer **opened** a Reminder or Manual mail, without that overwriting Notification `SENT`.
2. As a Staff Member, I want **soft bounce**, **hard bounce**, and **blocked** on the same timeline, so I can tell a bad address from a later open.
3. As a Staff Member, I want Appointment detail and the shop Notification list to badge Opened / Bounced from those events (`BLOCKED` counts as bounced).
4. As a Staff Member, I want dashboard Opened / Bounced counts for a time range (same events, first-in-range so daily bars sum to the headline).
5. As an operator, I want Brevo (or a stub) to POST opens and bounces to our webhook using the Notification UUID, so we do not poll the provider.

## Rules

- Channel is **EMAIL** in v1. Tracking is provider **Delivery Events**, append-only (`notification_delivery_events`).
- Worker `Notification.status` stays `PENDING` → `SENT` / `DEAD_LETTER` / `RETRY_SCHEDULED`. An `OPENED` or bounce on a `SENT` row does **not** change that enum. If SMTP already accepted and the process died before `SENT`, Brevo `request` / `sent` / `delivered` heals the open Notification and Reminder to `SENT` so retry does not send again. Do not add `opened_at` / `bounced` columns on `notifications`.
- Correlation Key = Notification UUID in a provider-mapped SMTP header (Brevo: `X-Mailin-custom`). Schema and JSON never store that header name.
- Ingest: `POST /webhooks/delivery/{provider}` with `APP_DELIVERY_WEBHOOK_SECRET` (`Authorization` Bearer, Token, or raw secret). Not User JWT. JSON object or array (max 100). Unknown Notification → 204. Duplicate `(notification_id, provider, provider_event_id)` → one row. Unmapped type → 204. Do not log recipient email.
- Staff reads: `GET /notifications/{id}` timeline **latest `occurredAt` first**. List `opened` / `bounced` = `EXISTS` on events (or timeline `OPENED` / `SOFT_BOUNCE` / `HARD_BOUNCE` / `BLOCKED`). `GET /notifications?appointmentId=` includes `events` so Appointment detail does not need a second round-trip for badges.
- Stats Opened / bounce use healed `occurred_at` (epoch ≥ 1e12 as millis) filtered to `[from, to)`. `bounced` includes provider `BLOCKED`.
- Customer does not list Notifications or events.

Tech: [../trd/email-tracking.md](../trd/email-tracking.md). Why: [../adr/0012-smtp-correlation-and-delivery-events.md](../adr/0012-smtp-correlation-and-delivery-events.md). Send path stays [notification.md](notification.md).
