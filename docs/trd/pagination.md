# Pagination (TRD)

Every **list GET** is paginated. Item GET by id is not. Staff `GET /appointments/{id}/reminders` is not a list GET (one row per offset).

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
- `GET /vehicles`
- `GET /customers`
- `GET /customers/{id}/vehicles`
- `GET /dealerships`

Scoped by the same read rules as the resource (own / home shop / Staff Customer directory / authenticated). Search (`q`) uses the same pages; see [search.md](search.md). Customer Appointment / Vehicle pages bind `customerId` from the token into the `WHERE` / ownership `JOIN`; they do not load all rows and filter in Java. Nested Customer / Vehicle / Dealership on an Appointment page is **one `findAllById` per table** after the page query (the token Customer / home Dealership is reused, not queried again). Staff Customer directory is page + one `findByCustomerIdIn`. Do not `JOIN FETCH` paginated `AppointmentEntity` (UUID FKs, not associations). DB outage still `503 RETRYABLE`, not a fake empty page. Default `size` and max `size` / `q` length come from config (`app.pagination`), not literals in controllers.
