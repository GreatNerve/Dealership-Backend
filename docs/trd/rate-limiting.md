# Rate limiting (TRD)

Spring Boot has no first-party limiter. Use **Bucket4j token bucket + Redis (Lettuce)**.

One bucket per **HTTP endpoint** (method + path). UUID path segments collapse to `{id}` so `GET /appointments/{id}` is one endpoint, not one bucket per Appointment. Login and register do **not** share a bucket. Listing Appointments does **not** spend create-Appointment tokens. There is **no** overlay that counts every route together.

Budgets stay small so a reviewer is never locked out for minutes: **15 requests / 60 seconds** per endpoint. Period is never longer than 60s. `Retry-After` ≤ 60s.

| Route | Identity | Budget |
| --- | --- | --- |
| `POST /auth/login` | IP | 15 / 60s |
| `POST /auth/register` | IP | 15 / 60s (own bucket) |
| Other unauthenticated | IP | 15 / 60s, that endpoint only |
| Authenticated `CUSTOMER` | `userId` | 15 / 60s, that endpoint only |
| Authenticated `STAFF` | `userId` | 15 / 60s, that endpoint only |
| `POST /webhooks/delivery/{provider}` | — | Not limited (provider bursts; Bearer secret) |

Redis key shape: `{ip\|user}:{id}:{METHOD}:{path}`. IP uses `RemoteAddr`. First `X-Forwarded-For` hop is used only when `app.rate-limit.trust-forwarded-for` is true **and** `RemoteAddr` is in `app.rate-limit.trusted-proxies`. Empty CIDRs = Cloudflare published ranges.

Headers on limited responses: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. On 429 also `Retry-After`. Body `RATE_LIMITED`.

Refill greedy so slots return as time passes. `app.rate-limit.enabled=false` in `test`.

Redis is not the uniqueness store and not a mail throttle.
