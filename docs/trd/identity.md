# Identity (TRD)

| Method | Path | Auth | Body | Success |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | none | email, password, role `CUSTOMER` or `DEALERSHIP_STAFF` | 201 User |
| POST | `/auth/login` | none | email, password | 200 `{ accessToken, tokenType, expiresIn }` — JWT **1 day**, no refresh in v1 |
| GET | `/me` | JWT | — | Current User + role + home Dealership if staff |

Errors: `400` validation, `401` bad credentials, `409` email taken, `429` rate limit.

Login/register keyed by IP in Bucket4j. See [rate-limiting.md](rate-limiting.md).

Table `users`: email unique **lowercase**, password hash (BCrypt), role. No timezone on User or Customer.
