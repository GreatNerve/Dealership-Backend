# Redis is HTTP rate limit only

Redis is **not** “this Vehicle is booked” and **not** a mail throttle.

It holds **Bucket4j token buckets** for the HTTP API, **one per endpoint** (method + path; UUID `{id}` collapsed). `userId` when logged in, IP on login/register. Login does not spend register tokens; a list GET does not spend a create POST. Every endpoint is **15 / 60s** so a reviewer is never locked out for minutes. 429 with `X-RateLimit-*` and `Retry-After`. Tests turn it off.

Spring has no first-party limiter. Token bucket refill is the behaviour I want (capacity, then slots come back as time passes). I am not writing Redis Lua for this.
