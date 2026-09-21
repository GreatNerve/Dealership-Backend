# Reminder (TRD)

Rows created with the Appointment. Offsets come from config `app.reminders.offsets` (Spring `Duration` list, env `APP_REMINDER_OFFSETS`, default `24h,2h`). Add `7d`, `6h`, `30m` without a code or migration change. Stored as `offset_minutes` (whole minutes, unique per Appointment + Schedule Version). There is no `ReminderType` enum — that set is not closed. **Due times are SQL:** `appointment.scheduled_at - (offset_minutes * interval '1 minute')`. No-show grace is config `app.reminders.no-show-grace` (default `1h`). See [time.md](time.md).

`UNIQUE (appointment_id, offset_minutes, schedule_version)`. Reminders own `schedule_version`: create and reschedule insert `COALESCE(MAX(schedule_version),0)+1`. Appointments do not store it.

Statuses: PostgreSQL `reminder_status` enum (`PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `DEAD_LETTER`, `CANCELLED`, `EXPIRED`). Java: `ReminderStatus`.

Staff read: `GET /appointments/{id}/reminders` (home Dealership). Do not expose a public Reminder list. Join Notification on `reminder_id` for the current schedule version; if no join, still return `notification.status = NOT_SCHEDULED`. Return stored `offset_minutes` and `reminders.scheduled_at` as `dueAt` (UTC Instant only). Do not format a second local datetime. Do not recompute offset subtraction in Java.

Do **not** load Appointments/Reminders into Java to subtract hours, expire windows, or no-show. Set-based SQL only. The worker loads a **lean projection** (ids, `scheduled_at`, `display_offset`, dealership name, optional customer name, contact for SMTP, `notify`) after claim — not the full entity graph.

`ReminderScheduler` (`@Scheduled`) calls `ReminderService.pollDue`. Claim SQL lives on `ReminderRepository` (`JdbcTemplate`, not JPA): `FOR UPDATE SKIP LOCKED`, **Claim Batch** from CPU count (`APP_WORKERS_CLAIM_BATCH=0` auto). Floor **18** is `500_000/28_800×2×0.5s` (10× assignment 50k on an 8-hour day); max 50 so a poll never `findAll`s. Lease **30s** in the **same claim transaction**. Send-window and Confirmed checks live in that `WHERE` so ineligible rows never enter the JVM. After claim, one `IN` load of mail facts for the batch — not `findById` per row. Heartbeat renews the lease during SMTP; a live `mail-*` owner blocks a second send (poller/replay locks are handed off). SMTP timeout < lease. Dead-letter Staff replay sets `PROCESSING` with a live `replay` lease so claim does not steal it and `MailWorker` can send. I/O outside the claim transaction. After claim, write `outbox_events` with a **snapshot payload** from the same JOIN (so the consumer does not reload Appointment + Customer + Vehicle). Not at Appointment create, not for far-future Reminders. Why the numbers: [../decision/scale.md](../decision/scale.md).

Send window (SQL; **if the worker goes down and then recovers**): `dueAt <= now() < dueAt + (nextDueAt − dueAt)/2` with `nextDueAt` = next Reminder `scheduled_at` or visit `scheduled_at`. Default: 24h is **T−24h → T−13h**; 2h is **T−2h → T−1h**. Remaining to next due greater than half the gap → send. At or past midpoint → set-based `EXPIRED`. Example: 24h recovered at T-20h → send. At T-12h → `EXPIRED`. 2h at T-90m → send; at T-30m → no. Cancel/reschedule → SQL status, workers must not send.

No-show (SQL): one `UPDATE` — Confirmed and `now() >= scheduled_at + interval '1 hour'` → `NO_SHOW_EXPIRED`; pending Reminders `EXPIRED`/`CANCELLED`. No `findAll` Confirmed rows.

Claim SQL shape is in [../architecture.md](../architecture.md). Indexes: [data-model.md](data-model.md).
