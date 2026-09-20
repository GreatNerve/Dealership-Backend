# Identity (TRD)

| Method | Path | Auth | Body | Success |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | none | email, password, role `CUSTOMER` or `DEALERSHIP_STAFF` | 201 User |
| POST | `/auth/login` | none | JSON `{ email, password }` **or** form `username` (email) + `password` (Swagger Authorize / OAuth2 password) | JSON 200 envelope `data`: `{ access_token, token_type, expires_in }`. Form 200 `{ access_token, token_type, expires_in }` (unwrapped for Swagger). JWT **1 day**, no refresh in v1 |
| GET | `/me` | JWT | — | Current User + role. Nested `customer` (`id`, `contact`) when the User is a Customer. Nested `homeDealership` (`id`, name, timezone, address) when Staff has a shop. Ids `customerId` / `homeDealershipId` stay. |

Staff create Customer is `POST /customers`, not `/auth/register`. See [customer.md](customer.md).

Errors: `400` validation (`VALIDATION_ERROR` for Bean Validation; same shape as other 400s), `401` bad credentials, `409` email taken, `429` rate limit.

JSON and form strings are sanitized (`Inputs`) before validation. Email is stored lowercase.

Login and register each have their own IP Bucket4j bucket (**15 / 60s**). They do not share tokens. See [rate-limiting.md](rate-limiting.md).

Table `users`: email unique **lowercase**, password hash (BCrypt), `user_role` enum. No timezone on User or Customer.
