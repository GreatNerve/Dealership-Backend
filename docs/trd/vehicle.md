# Vehicle (TRD)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/vehicles` | CUSTOMER | Body: `registrationNumber`, make, model, year. Unique **Vehicle Number**. 201. |
| POST | `/customers/{id}/vehicles` | DEALERSHIP_STAFF | Same body, for that Customer. See [customer.md](customer.md). |
| GET | `/vehicles?page&size&q` | CUSTOMER | Own Vehicles (`customer_id` from token), paginated + search. See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/vehicles/{id}` | CUSTOMER | `id AND customer_id` from token; 404 if missing or not owned. |
| GET | `/customers/{id}/vehicles?page&size&q` | DEALERSHIP_STAFF | That Customer’s Vehicles. See [customer.md](customer.md). |

Table `vehicles`: `customer_id`, `registration_number` unique (**uppercase**, hyphens/spaces stripped), make, model, year. Make/model sanitized (`Inputs`) then `@Size(max = 64)`. `year` 1950–2100. Never log the full **Vehicle Number**. Vehicle JSON includes nested `customer` (`id`, `contact`) plus `customerId`.

One Confirmed Appointment per `vehicle_id` is enforced on `appointments`, not here. See [appointment.md](appointment.md).
