# Notification (PRD)

## Stories

1. As an operator, I want stub delivery by default, so that the assignment demo is safe.
2. As an operator, I want a flag to send real mail via Brevo SMTP.
3. As an operator, I want 2–4 workers sending mail in parallel, because SMTP is slow.
4. As an operator, I want dead-lettered mail to be replayable with the same identity.
5. As a caller, I want Mock Appointments (`notify: false`) to append a file log and store delivery without sending email.
6. As a Staff Member, I want to GET Reminders and a Notification object for every offset on an Appointment at my home Dealership, including when that mail should send, so I can see SENT, still waiting, **Not Scheduled**, or DEAD_LETTER and why it failed, then replay.

## Rules

- `NotificationSender` abstraction. Mode `stub` or `smtp` for `notify: true`. `notify: false` appends `APP_NOTIFICATIONS_LOG_DIR` (`logs/notifications.log`).
- SMTP = **Brevo** (`APP_NOTIFICATIONS_MODE=smtp` + `SPRING_MAIL_*` in `.env`). No Mailhog in the default deps stack.
- 2–4 mail workers (default 2), prefetch 1 each, 30s lease with heartbeat (slow SMTP must not double-send).
- Notification/outbox only when the Reminder is due.
- SMTP failures (auth, timeout, 5xx) retry the same Notification key, exponential backoff + jitter (**30s**, then **60s / 2 min / 4 min**, cap **5 minutes**), max **5** attempts, then `DEAD_LETTER`. Invalid contact is permanent (no retry).
- Replay is Staff, home Dealership of that Appointment, else 404. Dead-letter only (`409 REPLAY_NOT_DEAD_LETTER` otherwise). Same notification idempotency key.
- Reminder = schedule. Notification = delivery. Do not collapse them.
- Staff mail status is `GET /appointments/{id}/reminders` (home Dealership, else 404). Not a shop-wide Notification list in v1. Not on Appointment list GET. Customer does not see `lastError`.
- Every Reminder item **always** includes a `notification` object. A Notification **row** exists only after the Reminder is due. Until then status is **Not Scheduled** (`NOT_SCHEDULED`), `id` is null — not a send failure, not JSON `null`.
- `EXPIRED` / `CANCELLED` on the Reminder means the window was skipped or the Appointment moved. Notification stays **Not Scheduled** unless a row was already written. Send failure is Notification `DEAD_LETTER` (or `RETRY_SCHEDULED` while still trying) plus `lastError`.
- Mail body uses **Booking Offset** as local wall time only (`Tuesday, 22 September 2026` / `10:00 PM`). No “UTC”, no offset in the mail. If the User has a **name**, greet `Hi {name},`; omit the greeting when name is absent. Vehicle make/model/year and **Vehicle Number**. No “2-hour reminder”. HTML + plain text. From `APP_MAIL_FROM` (`dheeraj@greatnerve.com`). Staff GET is **Dealership Timezone**. No Customer timezone field. See [../trd/time.md](../trd/time.md).
