# Vehicle (TRD)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/vehicles` | CUSTOMER | Body: vin, make, model, year. VIN unique. 201. |
| GET | `/vehicles?page&size&q` | CUSTOMER | Own Vehicles, paginated + search. See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/vehicles/{id}` | CUSTOMER | Own only; 404 if missing or not owned. |

Table `vehicles`: `customer_id`, `vin` unique (**uppercase**), make, model, year. Never log full VIN.

One Confirmed Appointment per `vehicle_id` is enforced on `appointments`, not here. See [appointment.md](appointment.md).
