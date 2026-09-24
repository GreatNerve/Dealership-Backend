# Notification pipeline: stub / Brevo SMTP, 2–8 workers, Delivery Events

Default sender is the **stub** (assignment-safe). A flag switches the same `NotificationSender` to **Brevo SMTP**. Keep SMTP (not the Transactional HTTP API) so the assignment demo and existing env stay one send path. Correlation is the **Notification** UUID in a provider-mapped custom header (Brevo: `X-Mailin-custom`). Adapters own header names; the ledger does not.

**2–8 leased workers** (default 2) send in parallel because SMTP is slow. Each consumer prefetch is 1. Redis stays off this path.

**System** path: due Reminder → Notification + `REMINDER_DUE` outbox → Rabbit → `MailWorker`. **Manual** path: Staff POST → Notification (`generation=MANUAL`, `reminder_id` null, subject/body stored) + `MANUAL_NOTIFICATION` outbox → same worker.

Before send (System): re-check the Appointment is still valid (Confirmed, not cancelled, still inside the send window). Then send, then persist SENT. Transient failure → retry. Exhausted retries → DEAD_LETTER.

**Delivery Events** are append-only. Webhook ingest (`POST /webhooks/delivery/{provider}`, Bearer `APP_DELIVERY_WEBHOOK_SECRET`) maps provider payloads to a generic enum (JSON array max 100, one transaction). Opens and bounces **do not** mutate worker `Notification.status`. List/stats derive opened/bounced with `EXISTS`; daily bounce/open slices use the first event in range so they sum to the distinct total. No snapshot columns on `notifications`.

Mock Appointments (`notify: false`) append `logs/notifications.log` and store Notification `SENT` on the **System** due path (same idempotency key). **Manual** send uses Notification Mode. **Replay** of dead letters reuses that identity.

Why not HTTP send, why not status snapshots: [../adr/0012-smtp-correlation-and-delivery-events.md](../adr/0012-smtp-correlation-and-delivery-events.md).
