# Integration tests

Real **PostgreSQL** via Testcontainers. Flyway runs. `ddl-auto=validate`. Bucket4j off. RabbitMQ optional here unless the test is outbox drain.

These tests prove the **database is the ledger**.

## Schema

- Migrations apply on empty Postgres.
- Hibernate validate matches Flyway.

## Uniqueness (single thread)

- Reminder rows for each configured offset (default 24h + 2h) on create.
- Inserting a second 24h Reminder for the same Appointment + schedule version fails the unique constraint.
- Second Confirmed Appointment on the same Vehicle fails the partial unique index when `one_confirmed` is true (default env).
- `APP_ONE_CONFIRMED_PER_VEHICLE=false` → two Confirmed rows, `one_confirmed=false`.
- Second Vehicle for the same Customer accepts.

## API idempotency table

- First create stores key, fingerprint, resource id, response. Key is unique per User.
- Replay same key + fingerprint **for that User** returns the same Appointment id; still one Appointment row.
- Same key, different fingerprint → conflict; still one Appointment row.
- Another User may use the same header value for their own create.
- Key expires after 24h (config); reuse after expiry is a new create.
- UTC midnight purge: `expires_at` in the past is deleted; unexpired rows stay. Notification `idempotency_key` is not this table.

## Lifecycle

- Customer or home-shop Staff cancel Confirmed → Appointment Cancelled; pending Reminders Cancelled; SENT rows untouched. Other customer / other shop → 404. Concurrent cancel/complete on the same row → `409 CONCURRENT_UPDATE` (optimistic `version`), not 500.
- Staff complete Confirmed → Appointment `COMPLETED`; pending Reminders Cancelled; Vehicle not Blocking. Customer → 403.
- Customer or home-shop Staff reschedule → Reminders `MAX(schedule_version)+1`; old Reminders Cancelled; new Reminder rows via SQL interval; unique key uses new version; `display_offset` from the new `scheduledAt`. Appointment has no `schedule_version`.
## Clock SQL (set-based)

- Create: 24h Reminder `scheduled_at` equals `appointment.scheduled_at - interval '24 hours'` (and 2h likewise). Assert in SQL/Testcontainers, not Java minus.
- Appointment 10 hours out → 24h row `EXPIRED` (past midpoint T−13h), 2h `PENDING`, without a Java loop. Appointment 20 hours out, never SENT → 24h `PENDING` (before T−13h). After a 24h System Notification is SENT, reschedule to 20h out → new 24h `EXPIRED`.
- No-show: one UPDATE, Confirmed with `scheduled_at` two hours ago → `NO_SHOW_EXPIRED`; Vehicle can take a new Confirmed. Suite must not `findAll` Confirmed into the app to decide.
- Outbox payload after claim contains the lean snapshot (`scheduled_at`, `display_offset`, dealership name, optional customer name, vehicle make/model/year, Vehicle Number); worker test must not require loading Vehicle/Dealership entities to format mail.

## Leases (DB only)

- Claim sets `PROCESSING` + `lease_expires_at` for up to **Claim Batch** rows (auto from CPUs, floor 18) in one SQL.
- Expired lease is claimable again (Reminders, outbox `PROCESSING`, and Manual Notifications).
- `markSent` on an expired lease is a no-op (Reminder lease for System; Notification lease for Manual).
- Two sequential claims of the same PENDING row: only one winner per claim SQL (concurrency layer does two threads).
- One poll with both offsets due claims **both** (batch, not `LIMIT 1`).

## Transactions

- Failure after Appointment insert and before Reminder insert (forced) → zero Appointment rows.

## Staff Reminder / Notification read

- Staff `GET /appointments/{id}/reminders` after reschedule returns **both** Schedule Versions (cancelled unsent + new PENDING/EXPIRED).
- After create, Staff `GET /appointments/{id}/reminders` returns one item per offset; `scheduleVersion` is 1; `offsetMinutes` and `dueAt` match `reminders.offset_minutes` / `reminders.scheduled_at` (UTC Instant, no `dueAtLocal`); each `notification.status` is `NOT_SCHEDULED` and `id` is null while not due. Never omit `notification`.
- After a successful send, nested Notification is `SENT` with `sentAt`.
- After a permanent failure, nested Notification is `DEAD_LETTER` with `lastError`; replay uses that id. Replay from another shop’s Staff is 404. Replay of `PENDING` / `RETRY_SCHEDULED` is `409 REPLAY_NOT_DEAD_LETTER`. Successful replay: Reminder `PROCESSING` with a live lease, Notification `PENDING`, stub/SMTP called **once** with the same idempotency key, then both `SENT`.
- Manual Notification unique key `appointmentId:MANUAL:notificationId`. Two Manual POSTs with **different** HTTP Idempotency-Keys → two rows. Same Idempotency-Key + same subject/body/Appointment → one row. CHECK: SYSTEM cannot store null `reminder_id`; MANUAL cannot store a Reminder id.
- `notification_delivery_events` unique `(notification_id, provider, provider_event_id)`: second insert of the same triple is a no-op/conflict, still one row. Webhook does not update `notifications.status`. Unknown Correlation Key → 204. JSON array payloads insert one event per mapped element in **one** transaction (max 100). `provider_event_id` over 255 still inserts (SHA-256 hex). Bounce/open `buckets[]` sum to the distinct totals (first event in range per Notification). Stats CTE filters healed `occurred_at` to `[from, to)` and still counts millis-as-seconds rows (year ~58699) by healing epoch ≥ 1e12.
- `notifications.dealership_id` matches the Appointment’s dealership on System insert and Manual insert. Staff list SQL is `dealership_id = home`. Instant `from`/`to` on list GETs is SQL `WHERE`, not Java filter after load.

## Identity login

- JSON `POST /auth/login` is the standard envelope with `data.access_token`. Form `username` (email) + `password` + `grant_type=password` stays unwrapped `{ access_token, token_type, expires_in }` so Swagger Authorize can fetch the JWT. Missing email still BCrypts a dummy hash before `401`.
- Missing/invalid JWT on a protected route → `401` envelope (`success: false`, `error: UNAUTHORIZED`). Customer hitting Staff-only routes → `403` envelope (`FORBIDDEN`). Never an empty body.

## List enrichment (no N+1)

- Hibernate statistics on: Customer `GET /appointments` with several Confirmed rows stays a bounded statement count (page + count + `IN` loads for Customer / Vehicle / Dealership / User name), not one query per nested row.
- Staff `GET /notifications` is page + count + event-flag `IN` + one `findAllById` per Appointment / Customer / Vehicle / Dealership / User name. Nested Appointment is on the JSON (customer, Vehicle Number, visit time), not UUID-only.

## Input validation / sanitize

- Invalid email, blank make, blank `scheduledAt` → `400 VALIDATION_ERROR`.
- Padded / control-character email stores lowercase. Dirty Vehicle Number stores the normalized plate.
- Form login password shorter than 8 → `400 VALIDATION_ERROR`.
- Bad IANA timezone → `400 INVALID_TIMEZONE`. `q` longer than max → `400 INVALID_Q`.
