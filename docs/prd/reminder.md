# Reminder (PRD)

## Stories

1. As a Customer, I want Reminders before `scheduledAt` at configured offsets (default 24 hours and 2 hours).
2. As a Customer, I must never get the same Reminder twice.
3. As an operator, I want due work to survive restarts, so that downtime does not drop Reminders.

## Rules

- Reminder offsets come from **config**, not hardcoded code. Default: 24 hours and 2 hours (assignment).
- Created in the same transaction as the Appointment. One row per offset.
- Unique on `(appointment, reminder type, schedule version)`.
- `notify: false` creates the rows but workers do not send until replay.
- Notification/outbox is created **only when due** (`scheduled_at <= now`). No Notification rows for days-ahead Reminders.
- Send window: a due Reminder may still send until the **next** offset (24h Reminder until 2h before; last offset until visit start), while Confirmed. Example: missed 24h, recovered at 20h before → send. Recovered at 1h before → 24h EXPIRED, 2h still sendable.
- Cancelled or rescheduled: unsent old Reminders cancelled (new schedule version). Workers re-check before send.
- Immediate confirmation mail is **not** v1.
- Due math is PostgreSQL `timestamptz` ± `interval`, set-based (no load-all in Java). Mail uses **Booking Offset**. Outbox carries a lean snapshot so the worker does not reload the full graph.
