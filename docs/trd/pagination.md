# Pagination (TRD)

Every **list GET** is paginated. Item GET by id is not. Staff `GET /appointments/{id}/reminders` is not a list GET (all Schedule Versions, one row per offset per version). `GET /notifications/stats`, `GET /appointments/stats`, and `GET /dashboard/stats` are not list GETs.

## Query

| Param | Default | Rules |
| --- | --- | --- |
| `page` | `0` | 0-based. Negative → `400`. |
| `size` | **`100`** | Min 1, max **`1000`**. Out of range → `400`. Omit `size` → 100. |

Config: `app.pagination.default-size` / `APP_PAGE_DEFAULT_SIZE` (default **100**), `max-size` / `APP_PAGE_MAX_SIZE` (**1000**). Not a Java literal in controllers.

Optional later: `sort`. v1 default sort is `created_at DESC`, then `id DESC` for stability.

## Response

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 100,
    "totalElements": 0,
    "totalPages": 0
  },
  "error": null,
  "message": null,
  "correlationId": "uuid"
}
```

Empty result is **200** with `items: []` and `totalElements: 0`, never 404.

Past the last page (`page >= totalPages` when total is > 0) is **200** with empty `items` and the real totals (clients can detect).

## Applies to

- `GET /appointments`
- `GET /notifications`
- `GET /vehicles`
- `GET /customers`
- `GET /customers/{id}/vehicles`
- `GET /dealerships`

Optional Instant `from`/`to` (ISO-8601) on `GET /appointments` (`scheduled_at`) and `GET /notifications` (`created_at`) and required on `GET /notifications/stats`, `GET /appointments/stats`, and `GET /dashboard/stats`. Shared binder in `com.dealership.shared` — not a second clock, not a `date=` helper. `from > to` → `400`. Omit both on lists = no date filter. Optional `bucket` (`DAY` \| `WEEK` \| `MONTH`) on those stats GETs; omit `bucket` for totals and empty `buckets[]`. `buckets[]` is zero-filled for every slice in the Instant range (shop TZ; first/last period may be partial). Bounce/open slices use the first event in range per Notification so they sum to the distinct totals. More than **400** slices (`StatsBucket.MAX_SLICES`) → `400 VALIDATION_ERROR` so a calendar-year `DAY` request is allowed and a multi-year `DAY` request does not allocate unbounded JSON. Staff dashboard must not send year-range `DAY` for a 7-day chart.

Scoped by the same read rules as the resource (own / home shop / Staff Customer directory / authenticated). Search (`q`) uses the same pages; see [search.md](search.md). Customer Appointment / Vehicle pages bind `customerId` from the token into the `WHERE` / ownership `JOIN`; they do not load all rows and filter in Java. Nested Customer / Vehicle / Dealership on an Appointment page is **one `findAllById` per table** after the page query (the token Customer / home Dealership is reused, not queried again). Staff Customer directory is page + one `findByCustomerIdIn`. Do not `JOIN FETCH` paginated `AppointmentEntity` (UUID FKs, not associations). DB outage still `503 RETRYABLE`, not a fake empty page. Default `size` and max `size` / `q` length come from config (`app.pagination`), not literals in controllers.
