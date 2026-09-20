# 0004 Redis is HTTP rate limiting, not a ledger

Spring Boot has no first-party rate limiter. The professional in-app package is Bucket4j (token bucket) with Redis via Lettuce so two app instances share a budget. Keys: `userId` when authenticated, IP for login. 429 includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`. Redis must not store “this Vehicle already has an Appointment” or “this Reminder was sent” — those are PostgreSQL unique constraints. Tests set `bucket4j.enabled=false`.

**Status:** accepted
