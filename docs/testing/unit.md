# Unit tests

No Spring context. No containers. Public functions and policies only.

## Reminder time

- Offsets come from config; default 24h and 2h (strings bound as SQL `interval` — do not reimplement subtraction in unit tests).
- Offset input (`+05:30`) normalises to the same Instant for HTTP parse.
- Format Instant + stored `display_offset` `+05:30` → Local Wall Time `22:00 UTC+05:30`, not `16:30Z` as the mail string.
- `ReminderMail` HTML + text: local clock `10:00 PM` (no “UTC”), Vehicle make/model/year + Vehicle Number, `Hi {name},` only when name is set, no “2-hour reminder”, dealership name HTML-escaped.
- Same Instant + Dealership Timezone for staff display helper (shop zone).
- Formatter never uses `ZoneId.systemDefault()` (EC2 us-east must not leak).

## Skip / expire / send windows

`ReminderRepository` SQL is **integration** (INSERT CASE EXPIRED, send-window `UPDATE`, claim `WHERE`). Unit tests must not fake `Instant.minus(24, HOURS)` as the source of truth.

- `scheduledAt` already past → create rejected (policy lives here; HTTP mapping is e2e).
- Cancelled or old schedule version → must not send (policy; SQL is integration).

## One Confirmed per Vehicle

- Policy function: a second Confirmed for the same Vehicle is a conflict when the cap is on. (The unique index is integration; env `APP_ONE_CONFIRMED_PER_VEHICLE` is the switch.)
- Two Vehicles, two Confirmed → allowed.

## Idempotency fingerprint

- Same key + same body → replay for that User.
- Same key + different body → reuse error.
- Same key, different User → not a replay.
- Missing key → invalid.

## Inputs

- `Inputs.sanitize` trims and drops ISO control / format / private-use / surrogate characters.
- `Inputs.email` then lowercases. Null stays null.
- Page `q` uses sanitize; blank after sanitize is no filter.

## Retry / backoff

- Transient vs permanent classification (timeout retry; SMTP auth and `AddressException` / invalid contact do not).
- Exponential backoff with jitter stays inside min/max.
- Attempt 5 → dead-letter, no next attempt.
- JWT secret shorter than 32 bytes is rejected (no zero-pad). Non-`dev`/`test` also rejects the committed default secret.
- **Claim Batch** `0` (auto) uses CPU count; floor 18 is 500k/day drain math (`500_000/28_800×2×0.5s`); pinned values outside 1–50 are rejected. Hikari `0` is `2×CPUs` (8–32). Tomcat max `0` is `16×CPUs` (50–200). Why: [../decision/scale.md](../decision/scale.md).

## No-show

- Policy: Confirmed and `now >= scheduledAt + 1 hour` → No-Show Expired. The job itself is SQL (integration).

## Rate-limit keys (no Redis)

- Customer vs staff capacities as numbers (default **15 / 60s** each; period never longer than 60s).
- `POST /auth/login` and `POST /auth/register` are different keys. `GET` vs `POST /appointments` are different keys. Two Appointment ids share `GET /appointments/{id}`.
- Live Redis behaviour is the `test-ratelimit` slice.

## What unit tests must not do

- Start Tomcat.
- Query JPA.
- Assert on private methods or SQL strings.
