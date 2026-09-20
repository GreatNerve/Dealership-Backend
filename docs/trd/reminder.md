# Reminder (TRD)

Rows created with the Appointment. Types from config `app.reminders.offsets` (default `24h`, `2h` → `TWENTY_FOUR_HOUR`, `TWO_HOUR`). Do not hardcode the durations in Java. **Due times are SQL:** `appointment.scheduled_at - CAST(:offset AS interval)`. See [time.md](time.md).

`UNIQUE (appointment_id, reminder_type, schedule_version)`.

Statuses: `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `DEAD_LETTER`, `CANCELLED`, `EXPIRED`.

Do **not** load Appointments/Reminders into Java to subtract hours, expire windows, or no-show. Set-based SQL only. The worker loads a **lean projection** (ids, `scheduled_at`, `display_offset`, dealership name, contact for SMTP) after claim — not the full entity graph.

Due poller: native SQL `FOR UPDATE SKIP LOCKED`, lease **30s** (configurable) in the **same claim transaction**. Send-window and Confirmed checks live in that `WHERE` so ineligible rows never enter the JVM. Heartbeat renews the lease during SMTP. SMTP timeout < lease. I/O outside the claim transaction. After claim, write `outbox_events` with a **snapshot payload** from the same JOIN (so the consumer does not reload Appointment + Customer + Vehicle). Not at Appointment create, not for far-future Reminders.

Send window (SQL): `reminder.scheduled_at <= now()` and Confirmed and `now()` before the next Reminder’s `scheduled_at` (or `appointment.scheduled_at` for the last offset). Example: 24h recovered at T-20h → send. At T-1h → set-based `EXPIRED`, not loaded. Cancel/reschedule → SQL status, workers must not send.

No-show (SQL): one `UPDATE` — Confirmed and `now() >= scheduled_at + interval '1 hour'` → `NO_SHOW_EXPIRED`; pending Reminders `EXPIRED`/`CANCELLED`. No `findAll` Confirmed rows.

Claim SQL shape is in [../architecture.md](../architecture.md). Indexes: [data-model.md](data-model.md).
