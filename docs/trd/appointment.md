# Appointment (TRD)

`Idempotency-Key` header **required** on POST (max 255). Unique per User (`UNIQUE (user_id, key)`). Replay identical fingerprint for **that User** → original body. Different body → `409 IDEMPOTENCY_KEY_REUSED`. Another User may reuse the same header value. Missing → `400`. Keys retained **24 hours** (`app.idempotency.ttl`), then a reused key is a new create. Expired rows (`expires_at < now()`) are deleted at **UTC midnight** (`IdempotencyScheduler`). Notification `idempotency_key` is the mail ledger and is not purged.

**Customer POST `/appointments`**

```json
{
  "vehicleId": "uuid",
  "dealershipId": "uuid",
  "scheduledAt": "2026-09-22T22:00:00+05:30",
  "notify": true
}
```

Customer id and contact from token/profile. `notify: false` → Mock Appointment: append `logs/notifications.log` and store Notification (no email). `notify: true` → Notification Mode (`stub` or `smtp`). `scheduledAt` → UTC Instant + **Booking Offset** (`display_offset`). No timezone field. Naive datetime (no offset) → `400`.

**Staff POST `/appointments`**

```json
{
  "customerId": "uuid",
  "vehicleId": "uuid",
  "scheduledAt": "2026-09-22T22:00:00+05:30",
  "notify": true
}
```

Home Dealership from membership. Extra `dealershipId` in body → ignore or `400`. Vehicle not owned by `customerId` → `409`. Booking Offset still from `scheduledAt` (mail does not use Dealership Timezone).

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/appointments/{id}` | Customer: `id + customer_id` from token; `scheduledAtLocal` from Appointment **Booking Offset**. Staff: `id + dealership_id` from home membership; `scheduledAtLocal` in **Dealership Timezone**. Else 404. Always return `scheduledAt` UTC + `displayOffset`. Nested `customer` (`id`, `contact`, optional `name`), `vehicle` (same as Vehicle GET), `dealership` (`id`, name, timezone, address). Ids stay on the Appointment for clients that already use them. See [time.md](time.md). |
| GET | `/appointments/{id}/reminders` | **Staff**, home Dealership, else 404. Current **Schedule Version** only. Not paginated (one row per configured offset). Each item: `offsetMinutes`, `dueAt` (UTC Instant = `reminders.scheduled_at`, when that mail should send), Reminder status, `notification` (never JSON `null`). No second local datetime. Client converts `dueAt` with Dealership Timezone. No Notification row yet → `status: NOT_SCHEDULED`, `id: null`. Replay needs a real id. See [notification.md](notification.md), [reminder.md](reminder.md), [time.md](time.md). |
| GET | `/appointments?page&size&q` | Same zone rules and nested Customer / Vehicle / Dealership as GET by id. Paginated + search. Customer SQL is `customer_id` from the token plus ownership `JOIN` on that Vehicle’s `customer_id`. Nested rows loaded with one `findAllById` per table after the page query (reuse token Customer / home Dealership). See [pagination.md](pagination.md), [search.md](search.md). |
| POST | `/appointments/{id}/cancel` | Confirmed only. Pending Reminders cancelled. 409 otherwise. |
| POST | `/appointments/{id}/reschedule` | Body `{ scheduledAt }`. New UTC Instant and **Booking Offset** from that value. Reminders own Schedule Version (`MAX(schedule_version)+1` on insert). 409 if not Confirmed. |

Create errors: `400` (Bean Validation `VALIDATION_ERROR`, `scheduledAt` already past, missing/oversized Idempotency-Key), `401/403`, `404`, `409` Vehicle already Confirmed, `429`. Request strings sanitized via `Inputs` before validation.

One DB transaction on create: Appointment row + Reminder rows via SQL `INSERT … SELECT` (`scheduled_at - CAST(:offset AS interval)`) + idempotency row. **No outbox or Notification rows at create.** Do not compute due times in Java.

When a Reminder is due, `ReminderScheduler` ticks `ReminderService`, which claims via `ReminderRepository` (SKIP LOCKED + lease, send-window in SQL), then `NotificationService` writes a **lean outbox snapshot** (JPA); `OutboxPublisher` SKIP LOCKED-claims and pushes to RabbitMQ; `MailWorker` sends from that payload.

Partial unique: `UNIQUE (vehicle_id) WHERE status = 'CONFIRMED' AND one_confirmed`. Create sets `one_confirmed` from `APP_ONE_CONFIRMED_PER_VEHICLE` (default `true`). `false` stores `one_confirmed=false` so the index does not apply.
