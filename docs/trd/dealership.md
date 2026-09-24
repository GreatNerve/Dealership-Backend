# Dealership (TRD)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/dealerships` | authenticated | Creator becomes Staff Member of this shop. Body: name, timezone id, address. Name/address sanitized. Timezone must be IANA (`400 INVALID_TIMEZONE`). 201. |
| GET | `/dealerships?page&size&q` | authenticated | Paginated + search (Customer picks a Venue). See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/dealerships/{id}` | authenticated | 200 or 404. Loading/error per [conventions.md](conventions.md). |
| GET | `/dashboard/stats` | Staff, home Dealership | Instant `from`/`to` required. Optional `bucket` (`DAY` \| `WEEK` \| `MONTH`) — omit for totals and empty `buckets[]`. JSON `{ appointments, notifications }` — same Stats shape as the resource stats GETs. Staff UI: calendar year (1 Jan–31 Dec shop TZ) without `bucket`; last 7 days with `bucket=DAY`. At most 400 slices when `bucket` is set. Customer → 403. |

Tables:

- `dealerships`: name, timezone (IANA **Dealership Timezone**), address. Staff Appointment GET formats `scheduledAtLocal` here. Mail does not.
- `dealership_staff`: `user_id` unique (one home Dealership in v1), `dealership_id`.

Staff `dealershipId` on Appointment create is always from this membership, never from the request body.
