# End-to-end tests

One running app. **JUnit in `src/test/java/com/dealership/e2e/`**, Testcontainers: **Postgres + RabbitMQ + Redis**. Mail = recording stub. Rate limits **off** except the dedicated rate-limit slice.

Drive through **HTTP** (`/api/v1`), then assert stub recorder **and** database. That is the assignment video, automated. Do not keep a Python client in `%TEMP%` — that was a one-off live sweep against localhost, not the suite.

```bash
./mvnw test
./mvnw test -Dgroups=e2e
```

## Happy path (must-ship)

1. Register/login (or `dev` test token).
2. Create Dealership or use seed; create Vehicle.
3. `POST /appointments` with `Idempotency-Key`, `scheduledAt` far enough for both windows (or due-work setup below).
4. Response 201. Database: one Appointment (`scheduled_at` UTC, `display_offset` from `scheduledAt`), one Reminder per configured offset (default two). JSON: `scheduledAt` UTC + `displayOffset`.
5. Force due (clock or `scheduled_at` already due while Appointment still in the future).
6. ReminderScheduler + MailWorker run.
7. Stub invoked **once per Reminder Offset**. Payload Local Wall Time with **Booking Offset** (e.g. `UTC+05:30`), not UTC-only and not the host zone. Notification rows `SENT`. Reminder rows `SENT`.
8. Logs contain appointment/reminder/notification ids, not raw contact.

Customer GET: `scheduledAtLocal` from Booking Offset. Staff GET: `scheduledAtLocal` in Dealership Timezone.

## Dual booking

- Customer body `{ vehicleId, dealershipId, scheduledAt }` → 201 at that Venue.
- Staff body `{ customerId, vehicleId, scheduledAt }` → 201 at **home** Dealership.
- Staff cannot create at another shop (no `dealershipId` in body, or 400 if sent).

## Client retry

- Same `Idempotency-Key` + same body twice → same Appointment id, one row, same Reminder count (not doubled).
- Same key, different body → 409.

## One per Vehicle

- Cap on (default): second Confirmed for the same Vehicle → 409. One row remains.
- `APP_ONE_CONFIRMED_PER_VEHICLE=false`: second Confirmed for the same Vehicle → 201.

## Cancel / reschedule

- Cancel before send → stub never called for those Reminders.
- Reschedule → old Reminders not sent; new rows from config offsets can be sent.

## Mock Appointment

- `notify: false` → Reminders exist; when due, `logs/notifications.log` is appended (no contact) and Notification is `SENT`. Stub/SMTP not used.

## Replay

- Force dead-letter (stub throws permanent).
- Staff `GET /appointments/{id}/reminders` → Notification `DEAD_LETTER` + `lastError`; take `notification.id`.
- `POST /notifications/{id}/replay` → stub called with the **same** idempotency key. Not a new key.

## Failures the e2e suite must cover

- Stub transient timeout → retry, then SENT, still one logical Reminder Offset.
- Invalid contact (permanent) → `DEAD_LETTER`, stub not hammered forever.
- App restart between create and due → overdue Reminder still sends once.

## Rate-limit slice (`test-ratelimit`)

- Login burst past 15 / 60s → 429, `Retry-After` ≤ 60s, `X-RateLimit-*`.
- Register still succeeds after that burst (login and register are separate buckets).
- Default `./mvnw test` does not enable this (flaky otherwise).

## Loading / error HTTP

- GET with DB down (or stopped container) → `503 RETRYABLE`, not `200 []` and not a fake empty page.

## Pagination and search

- Omit `size` → default **100** (`APP_PAGE_DEFAULT_SIZE`).
- `GET /appointments?page=0&size=20` still returns `{ items, page, size, totalElements, totalPages }` (client asked for 20).
- Same for `/vehicles`, `/customers`, `/customers/{id}/vehicles`, and `/dealerships`.
- `q=honda` filters items; totals are the filtered count. No match: 200, empty items.
- Empty shop: 200, `items: []`, `totalElements: 0`.
- `size=1001` → 400. `q` longer than 100 chars → 400.

## Capacity burst

`RequestCapacityTest` (tag `capacity`) fires concurrent list GETs plus concurrent Appointment creates (unique Vehicles, rate limits off). It asserts zero `5xx`, every request in the burst succeeds, and logs handled count + req/s. Floor is a slow-CI bound, not an EC2 soak. See [../trd/capacity-and-ec2.md](../trd/capacity-and-ec2.md).

## Reads

- Customer cannot GET another Customer’s Appointment (404).
- Staff cannot GET another Dealership’s Appointment (404).
- Staff `GET /customers?q=` returns `customerId` plus nested Vehicles (`vehicleId`) for booking. Staff `POST /customers` then `POST /customers/{id}/vehicles` then `POST /appointments`. Customer callers get 403 on the directory.

## Out of e2e v1

- Live Brevo.
- Browser/UI (there is none).
- EC2.
