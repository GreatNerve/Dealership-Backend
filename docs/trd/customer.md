# Customer (TRD)

Customer profile is created with Customer register (role `CUSTOMER`) **or** Staff `POST /customers`. Contact is the **email** (no phone in v1). Do not log full email; mask in logs (`j***@x.com`). Stub/Brevo payload uses that email. No timezone column on `customers`.

Staff Appointment create must pass a `customerId` that exists; Vehicle must belong to that Customer or `409`. Staff look those ids up here (search or the create responses), not by guessing UUIDs.

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/customers` | DEALERSHIP_STAFF | Walk-in User. Body: `email`, optional `name` (max 100), `password` (same rules as register: sanitize, `@Email`, password 8–100). Always role `CUSTOMER`. 201 `{ id, contact, name, vehicles: [] }`. `409 EMAIL_TAKEN`. |
| GET | `/customers?page&size&q` | DEALERSHIP_STAFF | Directory. `q` matches contact **or** nested Vehicle Number / make / model. Each item includes `id`, `contact`, optional `name`, and nested `vehicles` (`id`, `registrationNumber`, make, model, year). Paginated. Vehicles for the page are one `IN` load, not per Customer. User names for the page are one `findAllById`. See [pagination.md](pagination.md), [search.md](search.md). |
| GET | `/customers/{id}` | DEALERSHIP_STAFF | One Customer + Vehicles. 404 if missing. |
| GET | `/customers/{id}/vehicles?page&size&q` | DEALERSHIP_STAFF | That Customer’s Vehicles only. Same vehicle search fields as `GET /vehicles`. |
| POST | `/customers/{id}/vehicles` | DEALERSHIP_STAFF | Add a Vehicle for that Customer. Same body as `POST /vehicles`. 201. 404 if Customer missing. `409 REGISTRATION_TAKEN`. |

Customer callers get **403**. Do not expose another Customer’s directory to a Customer.
