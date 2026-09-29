# Dealership (TRD)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/dealerships` | authenticated | Creator becomes Staff Member of this shop. Body: name, timezone id, address. Name/address sanitized. Timezone must be IANA (`400 INVALID_TIMEZONE`). 201. |
| GET | `/dealerships?page&size&q` | authenticated | Paginated + search (Customer picks a Venue). See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/dealerships/{id}` | authenticated | 200 or 404. Loading/error per [conventions.md](conventions.md). |
| GET | `/dealerships/{id}/schedule` | authenticated | Weekly hours + `defaultCapacity` + `slotDurationMinutes` + `maxAdvanceDays` (env). Customer: any Dealership. Staff: home only, else 404. |
| PUT | `/dealerships/{id}/schedule` | Staff, home | Replace all 7 weekdays + `defaultCapacity`. Reject if Confirmed visits would fall outside the new hours or `booked >` new effective capacity (`409 SCHEDULE_CONFLICT`). |
| GET | `/dealerships/{id}/overrides` | Staff, home | List **Capacity Overrides**. Customer → 403. Other shop → 404. |
| POST | `/dealerships/{id}/overrides` | Staff, home | Body `{ fromDate, toDate, fromTime?, toTime?, capacity }`. Dates inclusive (ISO `YYYY-MM-DD`). Times optional pair (Dealership-local `HH:mm`). Overlap → `409 OVERRIDE_OVERLAPS`. Capacity/hours conflict with Confirmed → `409 SCHEDULE_CONFLICT`. |
| PATCH | `/dealerships/{id}/overrides/{overrideId}` | Staff, home | Same overlap and conflict rules. |
| DELETE | `/dealerships/{id}/overrides/{overrideId}` | Staff, home | 204. |
| GET | `/dealerships/{id}/slots?from&to` | JWT | Instant `from`/`to` required (`from` before `to`). Customer: any Dealership; range clipped to now … Max Advance exclusive end. Staff: home only. JSON `{ items: [{ start, capacity, booked, available }] }` — no PII. Closed/past/full still listed so the UI can cross them out (`available` 0). Cap the query span at 40 local days (`400 VALIDATION_ERROR`). |
| GET | `/dashboard/stats` | Staff, home Dealership | Instant `from`/`to` required. Optional `bucket` (`DAY` \| `WEEK` \| `MONTH`) — omit for totals and empty `buckets[]`. JSON `{ appointments, notifications }` — same Stats shape as the resource stats GETs. Staff UI: calendar year (1 Jan–31 Dec shop TZ) without `bucket`; last 7 days with `bucket=DAY`. At most 400 slices when `bucket` is set. Customer → 403. |

Tables:

- `dealerships`: name, timezone (IANA **Dealership Timezone**), address, `default_capacity`. Staff Appointment GET formats `scheduledAtLocal` here. Mail does not.
- `dealership_staff`: `user_id` unique (one home Dealership in v1), `dealership_id`.
- `dealership_hours`: `(dealership_id, weekday)` unique. `weekday` ISO 1=Monday … 7=Sunday. `closed` or `open_time`/`close_time` (close exclusive).
- `dealership_capacity_overrides`: `from_date`, `to_date` inclusive, optional `from_time`/`to_time`, `capacity`. Overlap is an application check (same-day non-overlapping time windows are allowed).

Staff `dealershipId` on Appointment create is always from this membership, never from the request body.

**Service Slot** math and last-seat lock: [appointment.md](appointment.md), [../decision/service-slot-capacity.md](../decision/service-slot-capacity.md).
