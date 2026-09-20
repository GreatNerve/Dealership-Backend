# Notification pipeline: stub / Brevo, 2–4 workers

Default sender is the **stub** (assignment-safe). A flag switches the same `NotificationSender` to **Brevo SMTP**. Locally that SMTP path hits **Mailhog**.

**2–4 leased workers** (default 2) send in parallel because SMTP is slow. Each consumer prefetch is 1. Redis stays off this path.

Before send: re-check the Appointment is still valid (Confirmed, not cancelled, still inside the send window). Then send, then persist SENT. Transient failure → retry. Exhausted retries → DEAD_LETTER / FAILED.

Mock Appointments (`notify: false`) append `logs/notifications.log` and store Notification `SENT` (same idempotency key). **Replay** of dead letters reuses that identity.
