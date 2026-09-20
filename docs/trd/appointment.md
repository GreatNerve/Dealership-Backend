# Appointment (TRD)

`Idempotency-Key` header **required** on POST (max 255). Replay identical fingerprint → original body. Different body → `409 IDEMPOTENCY_KEY_REUSED`. Missing → `400`. Keys retained **24 hours** (`app.idempotency.ttl`), then a reused key is a new create.

**Customer POST `/appointments`**

```json
{
  "vehicleId": "uuid",
  "dealershipId": "uuid",
  "scheduledAt": "2026-09-22T22:00:00+05:30",
  "notify": true
}
```

Customer id and contact from token/profile. `notify: false` → Mock Appointment. `scheduledAt` → UTC Instant + **Booking Offset** (`display_offset`). No timezone field. Naive datetime (no offset) → `400`.

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
| GET | `/appointments/{id}` | Customer: own only; `scheduledAtLocal` from Appointment **Booking Offset**. Staff: home Dealership only; `scheduledAtLocal` in **Dealership Timezone**. Else 404. Always return `scheduledAt` UTC + `displayOffset`. See [time.md](time.md). |
| GET | `/appointments?page&size&q` | Same zone rules as GET by id. Paginated + search. See [pagination.md](pagination.md), [search.md](search.md). |
| POST | `/appointments/{id}/cancel` | Confirmed only. Pending Reminders cancelled. 409 otherwise. |
| POST | `/appointments/{id}/reschedule` | Body `{ scheduledAt }`. New UTC Instant and **Booking Offset** from that value. New schedule version. 409 if not Confirmed. |

Create errors: `400` (including `scheduledAt` already past), `401/403`, `404`, `409` Vehicle already Confirmed, `429`.

One DB transaction on create: Appointment row + Reminder rows via SQL `INSERT … SELECT` (`scheduled_at - CAST(:offset AS interval)`) + idempotency row. **No outbox or Notification rows at create.** Do not compute due times in Java.

When a Reminder is due, the poller claims it (SKIP LOCKED + lease, send-window in SQL), writes a **lean outbox snapshot**, publisher pushes to RabbitMQ; consumer sends from that payload.

Partial unique: `UNIQUE (vehicle_id) WHERE status = 'CONFIRMED'`.
