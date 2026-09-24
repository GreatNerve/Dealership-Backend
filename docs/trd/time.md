# Time and timezones (TRD)

The clock is UTC. The payload already carries the offset. Do **not** ask for a Customer timezone field.

## Why (EC2)

Workers may run on EC2 in `us-east-1` while the Customer booked `22:00+05:30` (India). That is not a bug **if** we never use the host timezone: store Instant, compare Instant, format mail with the **Booking Offset** from `scheduledAt`. `ZoneId.systemDefault()` and `LocalDateTime.now()` are forbidden. JWT `issuedAt` / `expiration` use `TimeProvider.now()`, not a second `Instant.now()`.

## Persist

| Column | Meaning |
| --- | --- |
| `scheduled_at` (`timestamptz`) | UTC Instant. Reminder offsets, send window, no-show, leases. |
| `appointments.display_offset` | ISO-8601 offset taken from `scheduledAt` (`+05:30`). Mail and Customer GET. |
| `dealerships.timezone` | IANA id on the shop. Staff GET only. Not a Customer field. |

No `customers.timezone`. No timezone on register or on Appointment create besides what is already inside `scheduledAt`.

JVM and Postgres: UTC. Naive datetime (no offset) → `400`.

## In

```json
{ "scheduledAt": "2026-09-22T22:00:00+05:30" }
```

Parse offset, convert to Instant. Store `2026-09-22T16:30:00Z` and `display_offset = +05:30`. Reschedule: take Instant and offset from the new `scheduledAt`.

JSON must be valid: no trailing comma (`{ "scheduledAt": "..." }` not `{ "scheduledAt": "...", }`). Trailing comma → `400 MALFORMED_REQUEST`.

## Reminder send times

Create/reschedule does **not** send mail. It inserts one Reminder row per configured offset (`APP_REMINDER_OFFSETS`, default `24h,2h`). That row’s `scheduled_at` is the **send due Instant** (`visit scheduled_at − offset`). A Notification is created only when that Instant is due and the worker claims it. `notify: false` still claims: append `logs/notifications.log` (no contact) and store `SENT`. `notify: true` uses Notification Mode.

Staff `GET /appointments/{id}/reminders` is the table for one Appointment (**all Schedule Versions**: `scheduleVersion`, `offsetMinutes`, `dueAt`, Reminder status, nested Notification).

Example visit `2026-09-21T04:40:00+05:30` (`notify: true`):

| Offset | `offsetMinutes` | Send `dueAt` (UTC) | Local (Booking Offset) | At create ~`2026-09-21T02:37+05:30` |
| --- | --- | --- | --- | --- |
| 24h | 1440 | `2026-09-19T23:10:00Z` | `2026-09-20T04:40:00+05:30` | already past → Reminder `EXPIRED`, no mail |
| 2h | 120 | `2026-09-20T21:10:00Z` | `2026-09-21T02:40:00+05:30` | still ahead → Reminder `PENDING`; Notification around `02:40` |

If the visit is **less than 1 hour** away, both default offsets are past midpoint → both `EXPIRED` → no Notification. Book **before T−1h** for a 2h row that can still send (**T−2h → T−1h**). Book **before T−13h** for a first 24h mail (**T−24h → T−13h**), even if due is already past (T−20h still sends). Book at **T−3h**: 24h past midpoint (`EXPIRED`), 2h `PENDING`. After a 24h was already SENT, a reschedule inside 24h inserts 24h `EXPIRED` again.

Send window (SQL; **if the worker goes down and then recovers**): adjacent gap ÷ 2. `nextDueAt` = next Reminder `dueAt` or visit `scheduled_at`. Send while `dueAt <= now() < dueAt + (nextDueAt - dueAt) / 2`. Default:

| Offset | From (inclusive) | To (exclusive) | How |
| --- | --- | --- | --- |
| 24h | T−24h | T−13h | (24h−2h)/2 = 11h after due |
| 2h | T−2h | T−1h | (2h−0)/2 = 1h after due |

Miss that midpoint → `EXPIRED`, not a retry of that offset. Remaining to next due **greater than** half the gap → send. Remaining **≤** half → no. Poller/worker down from T−24h to T−22h → 24h still sends. Down to T−12h → 24h no. 2h recovered at T−90m → send; at T−30m → no.

## Out (JSON)

Always:

```json
{
  "scheduledAt": "2026-09-22T16:30:00Z",
  "displayOffset": "+05:30"
}
```

Plus `scheduledAtLocal` for the caller:

| Caller | Uses | Example |
| --- | --- | --- |
| Customer GET | Appointment `display_offset` | `2026-09-22T22:00:00+05:30` |
| Staff GET Appointment | Dealership Timezone | shop wall clock (`scheduledAtLocal` for Swagger/curl) |
| Staff GET `/appointments/{id}/reminders` | one Instant | `dueAt` UTC only. Client formats with Dealership **IANA** timezone from the shop. Not a second `dueAtLocal`. Not the browser zone. Not Booking Offset. |

## Mail

HTML + plain text from one `ReminderMail` (outbox snapshot). Date and clock from Instant + `display_offset`, **no UTC string in the mail**:

`Tuesday, 22 September 2026` / `10:00 PM`

Vehicle: make, model, year, **Vehicle Number**. If `users.name` is set, first line `Hi {name},`; otherwise no greeting. Subject `Service appointment — {dealership}` (not “2-hour reminder”). Not `16:30 UTC`. Not the EC2 local clock. Stub/file log may still include the offset for operators. From: `APP_MAIL_FROM` (`dheeraj@greatnerve.com`). Never log the name.

## Math (PostgreSQL, not Java)

No-show grace is config `app.reminders.no-show-grace` (default `1h`), bound as `interval`. Config offsets (`APP_REMINDER_OFFSETS`, default `24h,2h`) are stored as `offset_minutes`. Postgres does the arithmetic so EC2 and the JVM clock cannot drift from the ledger.

Insert Reminder due times (same transaction as create/reschedule): due Instant = visit − offset. Status uses the **same midpoint** as claim (`due + (nextDue − due)/2`, `nextDue` from `:nextOffsetMinutes` or visit). Already SENT this offset (System Notification) and `now() >= due` → `EXPIRED` (do not send 24h again after a move inside 24h). Else past midpoint → `EXPIRED`. Else `PENDING` (first book in-window still sends).

```sql
-- w.due / w.next_due from LATERAL (visit − offset, next offset or visit)
CASE
  WHEN EXISTS (SENT SYSTEM notification for this appointment + offset)
       AND now() >= w.due THEN 'EXPIRED'  -- already mailed this offset; new due past
  WHEN now() >= w.due + (w.next_due - w.due) / 2 THEN 'EXPIRED'
  ELSE 'PENDING'
END
```

Due claim already uses `scheduled_at <= now()`. Send window and no-show:

```sql
-- next_due = next reminder.scheduled_at, or appointment.scheduled_at for the last offset
-- send while now() < due_at + (next_due - due_at) / 2   -- adjacent gap ÷ 2
-- no-show
UPDATE appointments
SET status = 'NO_SHOW_EXPIRED'
WHERE status = 'CONFIRMED'
  AND now() >= scheduled_at + interval '1 hour';
```

Java must not persist `Instant.minus(24, HOURS)` as the Reminder clock. Parse `scheduledAt` for Instant + Booking Offset only (HTTP and mail).

## Optimizations (set-based, no full graph)

The application layer does not load every due row, compute times, and write back. Postgres does the set work; Java sends mail.

| Work | How |
| --- | --- |
| Reminder due times | `INSERT … SELECT` + `interval`, same transaction as create/reschedule |
| Skip closed / already-SENT windows | `CASE … EXPIRED` in that INSERT (midpoint + SENT EXISTS), not a Java loop |
| Close send window | `UPDATE reminders SET status = 'EXPIRED' WHERE …` (bounded, indexed) |
| No-show | one `UPDATE appointments … WHERE CONFIRMED AND now() >= scheduled_at + interval '1 hour'` |
| Claim | `SKIP LOCKED` **Claim Batch** (`APP_WORKERS_CLAIM_BATCH=0` auto from CPUs). Floor 18 = 500k/day drain per 500ms poll; max 50 so no `findAll`. Send-window + Confirmed in `WHERE`. Why: [../decision/scale.md](../decision/scale.md) |
| Mail | After claim, one JOIN returning a **lean projection**. Copy that into outbox `payload` jsonb (replicate what the mail needs). Consumer must not `findById` the full Appointment/Customer/Vehicle/Dealership graph |
| Indexes | Partial: due Reminders (`PENDING`/`RETRY_SCHEDULED`, `scheduled_at`); no-show Confirmed `scheduled_at`. List FKs: `appointments.customer_id`, `appointments.dealership_id`, `appointments (dealership_id, scheduled_at)`, `vehicles.customer_id`, `notifications.appointment_id` / `reminder_id` / `(dealership_id, created_at)`, `notification_delivery_events (notification_id)` |

Outbox snapshot fields: appointment id, notification id, generation, offset minutes (system), schedule version (system), `scheduled_at`, `display_offset`, dealership name, customer name (optional), vehicle make/model/year, **Vehicle Number** (mail only, never logged), contact (for SMTP, never logged). **Manual** snapshot also carries stored `subject`/`body`.

`display_offset` is never in a `WHERE`.


