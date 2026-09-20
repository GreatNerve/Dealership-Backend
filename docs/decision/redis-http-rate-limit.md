# Redis is HTTP rate limit only

Redis is **not** “this Vehicle is booked” and **not** a mail throttle.

It holds **Bucket4j token buckets** for the HTTP API: `userId` when logged in, IP on login. Staff get a higher budget than Customers; I do not skip Staff limits. 429 with `X-RateLimit-*` and `Retry-After`. Tests turn it off.

Spring has no first-party limiter. Token bucket refill is the behaviour I want (capacity, then slots come back as time passes). I am not writing Redis Lua for this.
