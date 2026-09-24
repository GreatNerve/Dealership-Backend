# Reminder (PRD)

## Stories

1. As a Customer, I want Reminders before `scheduledAt` at configured offsets (default 24 hours and 2 hours).
2. As a Customer, I must never get the same Reminder twice.
3. As an operator, I want due work to survive restarts, so that downtime does not drop Reminders.
4. As a Staff Member, I want to see **every Schedule Version** of Reminders on an Appointment (due, skipped, cancelled after reschedule, already SENT) with **offset minutes**, **version**, and **when that mail should send**, so I do not lose prior history.

## Rules

- Reminder offsets come from **config**, not hardcoded code. Default: `24h,2h`. Expand with `APP_REMINDER_OFFSETS=7d,24h,6h,2h` (hours, minutes, or days). No Java enum of types.
- Created in the same transaction as the Appointment (and again on reschedule). One row per offset.
- Unique on `(appointment, offset_minutes, schedule version)`.
- `notify: false` creates the rows; when due, workers append `logs/notifications.log` and store Notification `SENT` (no email). `notify: true` uses Notification Mode (stub or SMTP).
- Notification/outbox is created **only when due** (`scheduled_at <= now`). No Notification rows for days-ahead Reminders.
- At create/reschedule insert: use the **same send-window midpoint** as claim. Never SENT this offset and `now` still before midpoint → `PENDING` (send when claimed, including a first book at T−20h). Past midpoint → `EXPIRED`. Already SENT this offset (System) **and** the new due is past (`now >= visit − offset`, e.g. new visit less than 24h away after they already got the 24h) → `EXPIRED`, do not send that offset again. Already SENT but new due still in the future (moved further out) → `PENDING`. Example: first book 20h out → 24h `PENDING`. First book 10h out (past T−13h) → 24h `EXPIRED`. Reschedule to 20h after the 24h already SENT → 24h `EXPIRED`; 2h still `PENDING`.
- Send window (because if the worker goes down and then recovers): adjacent gap ÷ 2. Due time is when mail should go. Worker down at due, recovers in the first half of the gap → still send. Recovers past the midpoint → `EXPIRED`. `nextDueAt` is the next smaller offset’s due time, or visit `scheduledAt` for the last offset. Gap = `nextDueAt − dueAt`. Midpoint = `dueAt + gap/2`. Send while `dueAt <= now() < midpoint`. Remaining time to next due **greater than** half the gap → send. Remaining **less than** (or equal) half → `EXPIRED`, no mail for that offset. Not a 1-hour buffer. Not the full stretch to the next offset.

  Visit at **T**. Default `24h,2h`: gap 24h−2h = **22h**, half = **11h**. Last offset vs visit: gap 2h−0 = **2h**, half = **1h**.

  | Reminder | Due | Gap to next | Midpoint | Notification may send | After that |
  | --- | --- | --- | --- | --- | --- |
  | 24h | T−24h | 22h (to 2h) | **T−13h** | **T−24h → T−13h** | `EXPIRED`, no 24h mail |
  | 2h | T−2h | 2h (to visit) | **T−1h** | **T−2h → T−1h** | `EXPIRED`, no 2h mail |

  Recover at T−22h or T−20h (still before T−13h) → **send 24h**. Recover at T−12h → 24h **no**. Recover 2h at T−90m → **send**. Recover 2h at T−30m → **no**. First book at T−20h (never SENT, before T−13h) → **send 24h**. First book at T−12h / T−10h → 24h `EXPIRED`. Reschedule inside 24h **after** that 24h was already SENT → 24h `EXPIRED` again (do not mail 24h twice). 2h still `PENDING` until its window.
- Cancelled or rescheduled: unsent old Reminders cancelled; new rows get `MAX(schedule_version)+1` on `reminders` (Appointment is not versioned). Workers re-check before send. Reschedule must change Instant; reject past target and past current visit. Already SENT Notifications stay. Staff Reminder GET returns **all versions**, not current-only. Shop-wide Notification list does not invent rows for cancelled never-sent offsets.
- Immediate confirmation mail is **not** v1.
- Due math is PostgreSQL `timestamptz` ± `interval`, set-based (no load-all in Java). Mail uses **Booking Offset**. Outbox carries a lean snapshot so the worker does not reload the full graph. Each poll claims a **Claim Batch** from CPU count. Floor **18** is 500k/day drain (`500_000/28_800×2×0.5s`), not “10× the poll.” Cap 50 so a tick never loads every due row. Why: [../decision/scale.md](../decision/scale.md).
- Staff `GET /appointments/{id}/reminders` shows every version: `scheduleVersion`, `offsetMinutes`, and one send Instant (`dueAt` from `reminders.scheduled_at`, UTC). Order `scheduleVersion DESC`, then `offsetMinutes DESC`. The client formats `dueAt` with **Dealership Timezone** and lists **this visit** first, then **previous booking** after reschedule — do not show “schedule version N” in the UI. No `dueAtLocal`. That Instant is when that Reminder should send, not the visit `scheduledAt`. Nested Notification is always present (**Not Scheduled** until a row exists).
