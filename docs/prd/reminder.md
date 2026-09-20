# Reminder (PRD)

## Stories

1. As a Customer, I want Reminders before `scheduledAt` at configured offsets (default 24 hours and 2 hours).
2. As a Customer, I must never get the same Reminder twice.
3. As an operator, I want due work to survive restarts, so that downtime does not drop Reminders.
4. As a Staff Member, I want to see each Reminder on an Appointment (due or skipped) with **offset minutes** and **when that mail should send**, so I do not confuse “not due yet” with a failed send.

## Rules

- Reminder offsets come from **config**, not hardcoded code. Default: `24h,2h`. Expand with `APP_REMINDER_OFFSETS=7d,24h,6h,2h` (hours, minutes, or days). No Java enum of types.
- Created in the same transaction as the Appointment. One row per offset.
- Unique on `(appointment, offset_minutes, schedule version)`.
- `notify: false` creates the rows; when due, workers append `logs/notifications.log` and store Notification `SENT` (no email). `notify: true` uses Notification Mode (stub or SMTP).
- Notification/outbox is created **only when due** (`scheduled_at <= now`). No Notification rows for days-ahead Reminders.
- Send window: a due Reminder may still send until the **next** offset (24h Reminder until 2h before; last offset until visit start), while Confirmed. Example: missed 24h, recovered at 20h before → send. Recovered at 1h before → 24h EXPIRED, 2h still sendable.
- Cancelled or rescheduled: unsent old Reminders cancelled (new schedule version). Workers re-check before send.
- Immediate confirmation mail is **not** v1.
- Due math is PostgreSQL `timestamptz` ± `interval`, set-based (no load-all in Java). Mail uses **Booking Offset**. Outbox carries a lean snapshot so the worker does not reload the full graph.
- Staff `GET /appointments/{id}/reminders` shows `offsetMinutes` and one send Instant (`dueAt` from `reminders.scheduled_at`, UTC). The client formats that Instant with **Dealership Timezone**. No `dueAtLocal`. That Instant is when that Reminder should send, not the visit `scheduledAt`.
