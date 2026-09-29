# Service Slot capacity

Customers were able to confirm any future Instant at a Dealership. That is not how a shop runs: hours, holidays, and how many visits one half-hour can take.

## What I chose

- Discrete **Service Slots** (`APP_SLOT_DURATION`, default 30 minutes) in **Dealership Timezone**.
- One `defaultCapacity` per shop (seed `APP_SLOT_DEFAULT_CAPACITY` = 10) plus generic **Capacity Overrides** (date range, optional time, including holidays at 0). No overlapping overrides.
- Weekly hours: one interval per weekday. Create seed Mon–Sat 09:00–18:00, Sunday closed (test profile may seed 24h so existing clock-relative tests only need alignment).
- Customer create/reschedule: aligned + hours + capacity + `APP_MAX_ADVANCE_DAYS` (default 15, end of that local day). v1 is env-global so a later Dealership column can replace the read.
- Staff create/reschedule: **Service Slot** alignment only (walk-in). Those Confirmed rows still count; Customers cannot add when `booked >= capacity`.
- Last seat: `pg_advisory_xact_lock` on (Dealership, slot Instant), then `COUNT(*)` Confirmed, then insert — same transaction. Redis stays HTTP rate limit only.
- Hours/capacity writes that would leave Confirmed visits illegal → `409 SCHEDULE_CONFLICT`.

I rejected a denormalized seat-ledger table: cancel, complete, no-show, and reschedule would all have to heal `booked`. Count of Confirmed is the ledger.

I rejected Redis locks: project rule, and a crash would leak inventory.

Overlap of overrides is rejected on write (not “lowest capacity wins”) so Staff see one row as the truth for that range.

## Why Staff bypasses

Walk-in is physical. There is no walk-in UI in v1; Staff `POST /appointments` is that path. A later calendar book for Staff can add an explicit flag. Until then, role = bypass of hours, capacity, and Max Advance Days — not of the grid Instant.
