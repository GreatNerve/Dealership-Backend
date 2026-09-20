# Rate limiting (TRD)

Spring Boot has no first-party limiter. Use **Bucket4j token bucket + Redis (Lettuce)**.

Keys: `userId` when JWT present; IP for login/register. Coarse IP bucket on all routes.

Headers on limited responses: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. On 429 also `Retry-After`. Body `RATE_LIMITED`.

Starting budgets (configurable):

| Key | Bucket |
| --- | --- |
| Login / register (IP) | 5 / 15 minutes |
| Authenticated CUSTOMER | 60 / minute |
| Authenticated STAFF | 300 / minute |
| Coarse IP | 600 / minute |

Refill greedy so slots return as time passes. `bucket4j.enabled=false` in `test`.

Redis is not the uniqueness store and not a mail throttle.
