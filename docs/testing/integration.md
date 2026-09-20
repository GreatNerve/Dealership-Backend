# Integration tests

Real **PostgreSQL** via Testcontainers. Flyway runs. `ddl-auto=validate`. Bucket4j off. RabbitMQ optional here unless the test is outbox drain.

These tests prove the **database is the ledger**.

## Schema

- Migrations apply on empty Postgres.
- Hibernate validate matches Flyway.

## Uniqueness (single thread)

- Reminder rows for each configured offset (default 24h + 2h) on create.
- Inserting a second 24h Reminder for the same Appointment + schedule version fails the unique constraint.
- Second Confirmed Appointment on the same Vehicle fails the partial unique index.
- Second Vehicle for the same Customer accepts.

## API idempotency table

- First create stores key, fingerprint, resource id, response.
- Replay same key + fingerprint returns the same Appointment id; still one Appointment row.
- Same key, different fingerprint → conflict; still one Appointment row.
- Key expires after 24h (config); reuse after expiry is a new create.

## Lifecycle

- Cancel Confirmed → Appointment Cancelled; pending Reminders Cancelled; SENT rows untouched.
- Reschedule → schedule version bumps; old Reminders Cancelled; new Reminder rows via SQL interval; unique key uses new version; `display_offset` from the new `scheduledAt`.
## Clock SQL (set-based)

- Create: 24h Reminder `scheduled_at` equals `appointment.scheduled_at - interval '24 hours'` (and 2h likewise). Assert in SQL/Testcontainers, not Java minus.
- Appointment 10 hours out → 24h row `EXPIRED`, 2h `PENDING`, without a Java loop.
- No-show: one UPDATE, Confirmed with `scheduled_at` two hours ago → `NO_SHOW_EXPIRED`; Vehicle can take a new Confirmed. Suite must not `findAll` Confirmed into the app to decide.
- Outbox payload after claim contains the lean snapshot (`scheduled_at`, `display_offset`, dealership name); worker test must not require loading Vehicle/Dealership entities to format mail.

## Leases (DB only)

- Claim sets `PROCESSING` + `lease_expires_at`.
- Expired lease is claimable again.
- Two sequential claims of the same PENDING row: only one winner per claim SQL (concurrency layer does two threads).

## Transactions

- Failure after Appointment insert and before Reminder insert (forced) → zero Appointment rows.
