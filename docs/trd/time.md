# Time and timezones (TRD)

**No code until asked.** The clock is UTC. The payload already carries the offset. Do **not** ask for a Customer timezone field.

## Why (EC2)

Workers may run on EC2 in `us-east-1` while the Customer booked `22:00+05:30` (India). That is not a bug **if** we never use the host timezone: store Instant, compare Instant, format mail with the **Booking Offset** from `scheduledAt`. `ZoneId.systemDefault()` and `LocalDateTime.now()` are forbidden.

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
| Staff GET | Dealership Timezone | shop wall clock |

## Mail

Format Instant with `display_offset`:

`Tuesday, 22 September 2026 at 10:00 PM (UTC+05:30)`

Not `16:30 UTC` as the only time. Not the EC2 local clock. Stub payload uses the same string (no raw contact).

## Math (PostgreSQL, not Java)

`display_offset` is never in a `WHERE`. Config offsets (`24h`, `2h`) are bound as `interval`. Postgres does the arithmetic so EC2 and the JVM clock cannot drift from the ledger.

Insert Reminder due times (same transaction as the Appointment):

```sql
INSERT INTO reminders (id, appointment_id, reminder_type, schedule_version, scheduled_at, status, ...)
SELECT gen_random_uuid(),
       a.id,
       :reminderType,
       a.schedule_version,
       a.scheduled_at - CAST(:offset AS interval),
       CASE
         WHEN a.scheduled_at - CAST(:offset AS interval) <= now() THEN 'EXPIRED'
         ELSE 'PENDING'
       END,
       ...
FROM appointments a
WHERE a.id = :appointmentId;
```

Due claim already uses `scheduled_at <= now()`. Send window and no-show:

```sql
-- last offset: send while now() < appointment.scheduled_at
-- earlier offset: send while now() < next reminder.scheduled_at
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
| Skip already-past windows | `CASE … EXPIRED` in that INSERT, not a Java loop |
| Close send window | `UPDATE reminders SET status = 'EXPIRED' WHERE …` (bounded, indexed) |
| No-show | one `UPDATE appointments … WHERE CONFIRMED AND now() >= scheduled_at + interval '1 hour'` |
| Claim | `SKIP LOCKED` `LIMIT 1` (or small batch). Send-window + Confirmed in `WHERE` |
| Mail | After claim, one JOIN returning a **lean projection**. Copy that into outbox `payload` jsonb (replicate what the mail needs). Consumer must not `findById` the full Appointment/Customer/Vehicle/Dealership graph |
| Indexes | Partial: due Reminders (`PENDING`/`RETRY_SCHEDULED`, `scheduled_at`); no-show Confirmed `scheduled_at` |

Outbox snapshot fields: appointment id, reminder type, schedule version, `scheduled_at`, `display_offset`, dealership name, contact (for SMTP, never logged). Not full VIN, not unused columns.

`display_offset` is never in a `WHERE`.


