# Search (TRD)

Every **list GET** accepts an optional `q` in addition to pagination. Item GET by id is not searchable.

## Query

| Param | Default | Rules |
| --- | --- | --- |
| `q` | omitted | Trimmed. Empty/`blank` = no text filter. Max 100 characters; longer → `400`. Case-insensitive contains. |

`q` is applied **after** read-scope (own / home shop). `totalElements` / `totalPages` are counts of the **filtered** set.

Do not use `q` for other shops’ data. Staff search still cannot see another Dealership.

## What `q` matches

| List | Fields |
| --- | --- |
| `GET /appointments` | Vehicle VIN, make, model; Appointment `status`; Dealership name (Customer list). Optional exact `status` query param as well (`CONFIRMED` \| `CANCELLED` \| `NO_SHOW_EXPIRED`). |
| `GET /vehicles` | VIN, make, model |
| `GET /dealerships` | name, address |

No full-text engine in v1. SQL `ILIKE` / `lower(column) LIKE '%' \|\| lower(:q) \|\| '%'`. Index later if needed.

## Response

Same envelope as [pagination.md](pagination.md). No matches: **200**, `items: []`, `totalElements: 0`.
