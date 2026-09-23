# Reminder (PRD)

## Stories

1. As a Customer, I want Reminders before `scheduledAt` at configured offsets (default 24 hours and 2 hours).
2. As a Customer, I must never get the same Reminder twice.
3. As an operator, I want due work to survive restarts, so that downtime does not drop Reminders.
4. As a Staff Member, I want to see each Reminder on an Appointment (due or skipped) with **offset minutes** and **when that mail should send**, so I do not confuse “not due yet” with a failed send.

## Rules

- Reminder offsets come from **config**, not hardcoded code. Default: `24h,2h`. Expand with `APP_REMINDER_OFFSETS=7d,24h,6h,2h` (hours, minutes, or days). No Java enum of types.
- Created in the same transaction as the Appointment (and again on reschedule). One row per offset.
- Unique on `(appointment, offset_minutes, schedule version)`.
- `notify: false` creates the rows; when due, workers append `logs/notifications.log` and store Notification `SENT` (no email). `notify: true` uses Notification Mode (stub or SMTP).
- Notification/outbox is created **only when due** (`scheduled_at <= now`). No Notification rows for days-ahead Reminders.
- At create/reschedule insert: if that offset’s **due Instant is already ≤ now** → `EXPIRED`, **no mail** (no catch-up). Example: reschedule to a visit **12h** from now → 24h is `EXPIRED`; visit **more than 24h** out → 24h `PENDING` and may send when due (same as a normal create days ahead). Same rule for create and reschedule — far-future creates are unchanged.
- Send window (because if the worker goes down and then recovers): adjacent gap ÷ 2. Due time is when mail should go. Worker down at due, recovers in the first half of the gap → still send. Recovers past the midpoint → `EXPIRED`. `nextDueAt` is the next smaller offset’s due time, or visit `scheduledAt` for the last offset. Gap = `nextDueAt − dueAt`. Midpoint = `dueAt + gap/2`. Send while `dueAt <= now() < midpoint`. Remaining time to next due **greater than** half the gap → send. Remaining **less than** (or equal) half → `EXPIRED`, no mail for that offset. Not a 1-hour buffer. Not the full stretch to the next offset.

  Visit at **T**. Default `24h,2h`: gap 24h−2h = **22h**, half = **11h**. Last offset vs visit: gap 2h−0 = **2h**, half = **1h**.

  | Reminder | Due | Gap to next | Midpoint | Notification may send | After that |
  | --- | --- | --- | --- | --- | --- |
  | 24h | T−24h | 22h (to 2h) | **T−13h** | **T−24h → T−13h** | `EXPIRED`, no 24h mail |
  | 2h | T−2h | 2h (to visit) | **T−1h** | **T−2h → T−1h** | `EXPIRED`, no 2h mail |

  Recover at T−22h or T−20h (still before T−13h) → **send 24h**. Recover at T−12h → 24h **no**. Recover 2h at T−90m → **send**. Recover 2h at T−30m → **no**. Book or reschedule to T−3h / T−12h: 24h due already past → `EXPIRED` at insert; 2h still `PENDING`.
- Cancelled or rescheduled: unsent old Reminders cancelled; new rows get `MAX(schedule_version)+1` on `reminders` (Appointment is not versioned). Workers re-check before send. Reschedule must change Instant; reject past target and past current visit.
- Immediate confirmation mail is **not** v1.
- Due math is PostgreSQL `timestamptz` ± `interval`, set-based (no load-all in Java). Mail uses **Booking Offset**. Outbox carries a lean snapshot so the worker does not reload the full graph. Each poll claims a **Claim Batch** from CPU count. Floor **18** is 500k/day drain (`500_000/28_800×2×0.5s`), not “10× the poll.” Cap 50 so a tick never loads every due row. Why: [../decision/scale.md](../decision/scale.md).
- Staff `GET /appointments/{id}/reminders` shows `offsetMinutes` and one send Instant (`dueAt` from `reminders.scheduled_at`, UTC). The client formats that Instant with **Dealership Timezone**. No `dueAtLocal`. That Instant is when that Reminder should send, not the visit `scheduledAt`.
