# At-least-once, idempotent reminders

The assignment’s hard line: a Customer must never get the **same Reminder twice**, and I have to prove it.

I do not claim exactly-once SMTP. I claim **at-least-once processing** plus:

- unique `(appointment, offset_minutes, schedule version)`
- stable notification key `appointmentId:offsetMinutes:scheduleVersion` (System) and `appointmentId:MANUAL:notificationId` (Manual)
- unique Delivery Event `(notification_id, provider, provider_event_id)`
- Stripe-style `Idempotency-Key` on `POST /appointments` and `POST /appointments/{id}/notifications` (client retries)
- Manual Notification lease (`locked_by` / `lease_expires_at`) so two workers cannot SMTP the same Manual row; System uses the Reminder lease

If Brevo accepts and the process dies before I mark SENT, a retry may hit the provider again. That is proven in `AppointmentFlowTest.crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly`: stub ≥ 2, Notification row count = 1, same key, ends `SENT`.
