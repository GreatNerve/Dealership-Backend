# Uniqueness and concurrency

The brief: a Customer must never receive the same Reminder twice, and it must be **provable**.

Proof is not a comment. Proof is:

1. Unique `(appointment_id, reminder_type, schedule_version)`.
2. Unique notification idempotency key.
3. **Two workers, one send** for that Reminder Type.

## Concurrent workers

- Two threads/processes claim the same due Reminder.
- Exactly one Notification send on the stub.
- The other worker either skipped the locked row or no-op’d on the unique key.
- Database: one `SENT` Notification for that key.

## Concurrent creates

- Two `POST /appointments` with the **same** Idempotency-Key → one Appointment, one winner 201, the other 201 replay or 409 in-flight — never two Appointments.
- Two `POST /appointments` for the **same Vehicle**, different keys → one 201, one 409. One Confirmed row.

## Crash windows (e2e or integration + stub)

- Crash after claim, before send → lease expires (heartbeat stopped), second worker sends **once**.
- Slow SMTP with heartbeat still running → second worker must **not** send.
- Crash after stub success, before DB SENT → retry uses the same key; stub may be called again (at-least-once). Assert we do not create a **second Reminder Type** or a second Notification **row** with a new key. Document the stub double-call as the honest limit.

## What “same Reminder twice” means

Same Appointment + same Reminder Type + same schedule version. A reschedule is a **new** schedule version and may notify again. That is not a duplicate; that is a new visit time.
