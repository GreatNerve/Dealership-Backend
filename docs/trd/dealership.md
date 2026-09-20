# Dealership (TRD)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/dealerships` | authenticated | Creator becomes Staff Member of this shop. Body: name, timezone id, address. 201. |
| GET | `/dealerships?page&size&q` | authenticated | Paginated + search (Customer picks a Venue). See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/dealerships/{id}` | authenticated | 200 or 404. Loading/error per [conventions.md](conventions.md). |

Tables:

- `dealerships`: name, timezone (IANA **Dealership Timezone**), address. Staff Appointment GET formats `scheduledAtLocal` here. Mail does not.
- `dealership_staff`: `user_id` unique (one home Dealership in v1), `dealership_id`.

Staff `dealershipId` on Appointment create is always from this membership, never from the request body.
