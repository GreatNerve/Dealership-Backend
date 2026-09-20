# Pagination (TRD)

Every **list GET** is paginated. Item GET by id is not.

## Query

| Param | Default | Rules |
| --- | --- | --- |
| `page` | `0` | 0-based. Negative → `400`. |
| `size` | `20` | Min 1, max `100`. Out of range → `400`. |

Optional later: `sort`. v1 default sort is `created_at DESC`, then `id DESC` for stability.

## Response

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Empty result is **200** with `items: []` and `totalElements: 0`, never 404.

Past the last page (`page >= totalPages` when total is > 0) is **200** with empty `items` and the real totals (clients can detect).

## Applies to

- `GET /appointments`
- `GET /vehicles`
- `GET /dealerships`

Scoped by the same read rules as the resource (own / home shop / authenticated). Search (`q`) uses the same pages; see [search.md](search.md). DB outage still `503 RETRYABLE`, not a fake empty page.
