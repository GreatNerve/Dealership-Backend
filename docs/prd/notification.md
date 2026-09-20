# Notification (PRD)

## Stories

1. As an operator, I want stub delivery by default, so that the assignment demo is safe.
2. As an operator, I want a flag to send real mail via Brevo SMTP.
3. As a developer, I want Mailhog in local Docker, so that SMTP is proven without emailing humans.
4. As an operator, I want 2–4 workers sending mail in parallel, because SMTP is slow.
5. As an operator, I want dead-lettered mail to be replayable with the same identity.
6. As a caller, I want Mock Appointments (notify-off) until replay or a later enable.

## Rules

- `NotificationSender` abstraction. Mode `stub` or `smtp`.
- SMTP = Brevo in real mode; Mailhog locally.
- 2–4 mail workers (default 2), prefetch 1 each, 30s lease with heartbeat (slow SMTP must not double-send).
- Notification/outbox only when the Reminder is due.
- Transient failures retry; permanent failures dead-letter in the database.
- Mail Replay uses the same notification idempotency key.
- Reminder = schedule. Notification = delivery. Do not collapse them.
- Mail body uses **Booking Offset** stored from `scheduledAt` (`10:00 PM UTC+05:30`), not UTC and not the EC2 host clock. Staff GET is **Dealership Timezone**. No Customer timezone field. See [../trd/time.md](../trd/time.md).
