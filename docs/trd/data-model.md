# Data model (TRD)

All tables: `id uuid PK`, `created_at`, `updated_at` timestamptz.

| Table | Notes |
| --- | --- |
| `users` | email unique **lowercase**, BCrypt hash, role |
| `dealerships` | name, timezone, address |
| `dealership_staff` | `user_id` unique, `dealership_id` |
| `customers` | `user_id` unique, contact |
| `vehicles` | `customer_id`, `vin` unique **uppercase**, make, model, year |
| `appointments` | customer, vehicle, dealership, `scheduled_at` (UTC), `display_offset` (from `scheduledAt`, e.g. `+05:30`), status, `created_by_*`, `schedule_version`, `notify`, optimistic `version` |
| `idempotency_keys` | key unique, fingerprint, resource_id, status, response, expires_at (retain **24h**, then reusable) |
| `reminders` | appointment, reminder_type (from config offsets), schedule_version, scheduled_at, status (`PENDING` \| `PROCESSING` \| `RETRY_SCHEDULED` \| `SENT` \| `DEAD_LETTER` \| `CANCELLED` \| `EXPIRED`), attempts, next_attempt_at, last_error, locked_by, locked_at, lease_expires_at |
| `notifications` | reminder_id, appointment_id, notification_type, idempotency_key unique, status (`PENDING` \| `PROCESSING` \| `RETRY_SCHEDULED` \| `SENT` \| `DEAD_LETTER` \| `CANCELLED`), attempts, next_attempt_at, last_error, sent_at |
| `outbox_events` | event_type, aggregate_id, payload jsonb, status, attempts, lease, published_at |

Constraints:

- `UNIQUE (vehicle_id) WHERE status = 'CONFIRMED'` on appointments
- `UNIQUE (appointment_id, reminder_type, schedule_version)` on reminders
- `UNIQUE (idempotency_key)` on notifications and on `idempotency_keys`

Partial indexes (due-work burst):

- `reminders (scheduled_at)` WHERE `status IN ('PENDING','RETRY_SCHEDULED')`
- `appointments (scheduled_at)` WHERE `status = 'CONFIRMED'` (no-show job)

