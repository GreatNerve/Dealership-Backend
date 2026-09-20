# At-least-once, idempotent reminders

The assignment’s hard line: a Customer must never get the **same Reminder twice**, and I have to prove it.

I do not claim exactly-once SMTP. I claim **at-least-once processing** plus:

- unique `(appointment, offset_minutes, schedule version)`
- stable notification key `appointmentId:offsetMinutes:scheduleVersion`
- Stripe-style `Idempotency-Key` on `POST /appointments` (a different problem: client retries)

If Brevo accepts and the process dies before I mark SENT, a retry may hit the provider again. I document that instead of lying.
