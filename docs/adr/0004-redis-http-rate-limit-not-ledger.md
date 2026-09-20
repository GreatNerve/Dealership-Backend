# 0004 Redis is HTTP rate limiting, not a ledger

Spring Boot has no first-party rate limiter. The professional in-app package is Bucket4j (token bucket) with Redis via Lettuce so two app instances share a budget. Keys: `userId` or IP **plus** HTTP endpoint (method + path; UUID segments collapsed to `{id}`). Not one global bucket across routes. Default **15 requests / 60 seconds** per endpoint (window never longer than 60s) so a reviewer is not locked out. 429 includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`. Redis must not store “this Vehicle already has an Appointment” or “this Reminder was sent” — those are PostgreSQL unique constraints. Tests set `app.rate-limit.enabled=false`.

**Status:** accepted
