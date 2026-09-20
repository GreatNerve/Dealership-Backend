# Notification (PRD)

## Stories

1. As an operator, I want stub delivery by default, so that the assignment demo is safe.
2. As an operator, I want a flag to send real mail via Brevo SMTP.
3. As a developer, I want Mailhog in local Docker, so that SMTP is proven without emailing humans.
4. As an operator, I want 2–4 workers sending mail in parallel, because SMTP is slow.
5. As an operator, I want dead-lettered mail to be replayable with the same identity.
6. As a caller, I want Mock Appointments (`notify: false`) to append a file log and store delivery without sending email.
7. As a Staff Member, I want to GET Reminders and a Notification object for every offset on an Appointment at my home Dealership, including when that mail should send, so I can see SENT, still waiting, **Not Scheduled**, or DEAD_LETTER and why it failed, then replay.

## Rules

- `NotificationSender` abstraction. Mode `stub` or `smtp` for `notify: true`. `notify: false` appends `APP_NOTIFICATIONS_LOG_DIR` (`logs/notifications.log`).
- SMTP = Brevo in real mode; Mailhog locally.
- 2–4 mail workers (default 2), prefetch 1 each, 30s lease with heartbeat (slow SMTP must not double-send).
- Notification/outbox only when the Reminder is due.
- Transient failures retry; permanent failures dead-letter in the database.
- Mail Replay uses the same notification idempotency key.
- Reminder = schedule. Notification = delivery. Do not collapse them.
- Staff mail status is `GET /appointments/{id}/reminders` (home Dealership, else 404). Not a shop-wide Notification list in v1. Not on Appointment list GET. Customer does not see `lastError`.
- Every Reminder item **always** includes a `notification` object. A Notification **row** exists only after the Reminder is due. Until then status is **Not Scheduled** (`NOT_SCHEDULED`), `id` is null — not a send failure, not JSON `null`.
- `EXPIRED` / `CANCELLED` on the Reminder means the window was skipped or the Appointment moved. Notification stays **Not Scheduled** unless a row was already written. Send failure is Notification `DEAD_LETTER` (or `RETRY_SCHEDULED` while still trying) plus `lastError`.
- Mail body uses **Booking Offset** stored from `scheduledAt` (`10:00 PM UTC+05:30`), not UTC and not the EC2 host clock. Staff GET is **Dealership Timezone**. No Customer timezone field. See [../trd/time.md](../trd/time.md).
