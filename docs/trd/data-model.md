# Data model (TRD)

All tables: `id uuid PK`, `created_at`, `updated_at` timestamptz.

| Table | Notes |
| --- | --- |
| `users` | email unique **lowercase**, optional `name`, BCrypt hash, `user_role` |
| `dealerships` | name, timezone, address |
| `dealership_staff` | `user_id` unique, `dealership_id` |
| `customers` | `user_id` unique, contact |
| `vehicles` | `customer_id`, `registration_number` unique **uppercase** (**Vehicle Number**), make, model, year |
| `appointments` | customer, vehicle, dealership, `scheduled_at` (UTC), `display_offset` (from `scheduledAt`, e.g. `+05:30`), `appointment_status`, `created_by_*` (`user_role`), `notify` (false → `logs/notifications.log`, true → stub/SMTP), `one_confirmed` (from `APP_ONE_CONFIRMED_PER_VEHICLE`), optimistic `version` |
| `idempotency_keys` | `user_id`, key unique together, fingerprint, resource_id, `idempotency_status`, response, expires_at (retain from config, default **24h**). Expired rows deleted at UTC midnight. |
| `reminders` | appointment, `offset_minutes`, schedule_version (`MAX+1` on insert after cancelUnsent), scheduled_at, `reminder_status`, attempts, next_attempt_at, last_error, locked_by, lease_expires_at |
| `notifications` | reminder_id, appointment_id, `offset_minutes`, idempotency_key unique, `notification_status`, attempts, next_attempt_at, last_error, sent_at |
| `outbox_events` | `outbox_event_type`, aggregate_id, payload jsonb, `outbox_status`, attempts, lease, published_at |

## Schema types

Closed sets are **PostgreSQL ENUM** types (and matching Java enums). Not `varchar`. Native SQL binds `Enum.name()` / named parameters; it does not invent a second spelling. **Reminder Offset** is not a closed set: it is `offset_minutes int` from config.

| PG type | Values |
| --- | --- |
| `user_role` | `CUSTOMER`, `DEALERSHIP_STAFF` |
| `appointment_status` | `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW_EXPIRED` |
| `idempotency_status` | `STARTED`, `COMPLETED` |
| `reminder_status` | `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `DEAD_LETTER`, `CANCELLED`, `EXPIRED` |
| `notification_status` | `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `DEAD_LETTER`, `CANCELLED` |
| Java `NotificationStatus` | Same as PG, plus **`NOT_SCHEDULED`** for Staff GET when no `notifications` row exists. Never stored. |
| `outbox_event_type` | `REMINDER_DUE` |
| `outbox_status` | `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `PUBLISHED` |

- `UNIQUE (vehicle_id) WHERE status = 'CONFIRMED' AND one_confirmed` on appointments (`APP_ONE_CONFIRMED_PER_VEHICLE`)
- `UNIQUE (appointment_id, offset_minutes, schedule_version)` on reminders
- `UNIQUE (idempotency_key)` on notifications
- `UNIQUE (user_id, key)` on `idempotency_keys`

Partial indexes (due-work burst):

- `reminders (scheduled_at)` WHERE `status IN ('PENDING','RETRY_SCHEDULED')`
- `appointments (scheduled_at)` WHERE `status = 'CONFIRMED'` (no-show job)

List/FK indexes (page GETs; Postgres does not index FKs by itself):

- `appointments (customer_id)`, `appointments (dealership_id)`
- `vehicles (customer_id)`
- `notifications (appointment_id)`, `notifications (reminder_id)`

