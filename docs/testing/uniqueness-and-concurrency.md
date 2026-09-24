# Uniqueness and concurrency

The brief: a Customer must never receive the same Reminder twice, and it must be **provable**.

Proof is not a comment. Proof is:

1. Unique `(appointment_id, offset_minutes, schedule_version)`.
2. Unique notification idempotency key (System and Manual shapes).
3. Unique `(notification_id, provider, provider_event_id)` on Delivery Events.
4. **Two workers, one send** for that Reminder Offset (`concurrentClaimsSendOnce`).
5. Crash after provider accept / before durable `SENT` → retry **same key**, still **one** Notification row (`crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly`).

## Concurrent workers

- Two threads/processes claim the same due Reminder (a **Claim Batch** still uses `SKIP LOCKED` per row).
- Exactly one Notification send on the stub for that Reminder Offset.
- The other worker either skipped the locked row or no-op’d on the unique key.
- Database: one `SENT` Notification for that key.

## Concurrent creates

- Two `POST /appointments` with the **same** Idempotency-Key **and User** → one Appointment, one winner 201, the other 201 replay or 409 in-flight — never two Appointments.
- Two `POST /appointments/{id}/notifications` with the **same** Idempotency-Key **and User** → one Manual Notification.
- Two `POST /appointments` for the **same Vehicle**, different keys, cap on (`APP_ONE_CONFIRMED_PER_VEHICLE=true`) → one 201, one 409. One Confirmed row.
- Cap off (`false`) → both 201. Two Confirmed rows. Index still exists; those rows have `one_confirmed=false`.

## Crash windows (e2e or integration + stub)

- Crash after claim, before send → lease expires (heartbeat stopped), second worker sends **once**.
- Slow SMTP with heartbeat still running → second worker must **not** send.
- Crash after stub/SMTP success, before DB `SENT` → retry uses the **same** idempotency key; stub **may** be called again (at-least-once to the provider). Assert **one** Notification row for that key (no second Reminder Offset, no new key). Proven by `AppointmentFlowTest.crashAfterProviderAcceptBeforeSentRetriesSameKeyOnly`.

## What “same Reminder twice” means

Same Appointment + same Reminder Offset minutes + same schedule version. A reschedule is a **new** schedule version and may notify again. That is not a duplicate; that is a new visit time. A **Manual** send is a new Notification id (new key). Two webhook deliveries with the same `provider_event_id` are one event.
