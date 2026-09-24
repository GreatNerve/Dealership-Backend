# Search (TRD)

Every **list GET** accepts an optional `q` in addition to pagination. Item GET by id is not searchable.

## Query

| Param | Default | Rules |
| --- | --- | --- |
| `q` | omitted | Sanitized (`Inputs`: trim + strip controls). Empty/`blank` after that = no text filter. Max 100 characters; longer → `400`. Case-insensitive contains. |

`q` is applied **after** read-scope (own / home shop). `totalElements` / `totalPages` are counts of the **filtered** set.

Customer lists take `customerId` from the JWT (never from a query param). `GET /appointments` for a Customer is `WHERE a.customer_id = :customerId` **and** `JOIN vehicles v ON v.id = a.vehicle_id AND v.customer_id = :customerId`. `GET /vehicles` is `WHERE v.customer_id = :customerId`. Item GET is `id AND customer_id` (Customer) or `id AND dealership_id` (Staff). Staff Appointment list is home Dealership, not the Staff user’s customer id.

JOIN on vehicle / dealership is for that scope plus `q`. Nested JSON is still batched `findAllById` after the page. Do not `JOIN FETCH` paginated `AppointmentEntity`.

Do not use `q` for other shops’ data. Staff search still cannot see another Dealership.

## What `q` matches

| List | Fields |
| --- | --- |
| `GET /appointments` | Vehicle Number, make, model; Appointment `status`; Dealership name (Customer list). Optional exact `status` query param (`CONFIRMED` \| `CANCELLED` \| `COMPLETED` \| `NO_SHOW_EXPIRED`). Optional Instant `from`/`to` on `scheduled_at` (not part of `q`). |
| `GET /notifications` | Customer name, Vehicle Number, make, model; `generation`; `channel`; worker `status`. Optional exact filters (`status`, `generation`, `channel`, `appointmentId`, `hasEvent`, Instant `from`/`to`) are query params, not `q`. |
| `GET /vehicles` | Vehicle Number, make, model (own Vehicles) |
| `GET /customers` | contact; Vehicle Number, make, model (any Vehicle of that Customer) |
| `GET /customers/{id}/vehicles` | Vehicle Number, make, model |
| `GET /dealerships` | name, address |

No full-text engine in v1. JPA `LIKE` (case-insensitive contains). Hibernate still emits SQL `LIKE`; do not write native SQL for list search. Index later if needed.

## Response

Same envelope as [pagination.md](pagination.md). No matches: **200**, `items: []`, `totalElements: 0`.
