# Capacity and EC2 (TRD)

**Why these numbers:** the assignment PDF is 50k Appointments/day across 500 Dealerships. We size **10× (500k/day)** as review headroom, not because anyone measured 500k. Pools are computed from **this JVM’s CPU count** so a laptop, Docker, and later EC2 do not share a guessed Hikari=20 / batch=10. Full arithmetic and “why 18 / why 2× CPUs / why Compose 2+1+0.5+4” is [../decision/scale.md](../decision/scale.md).

At boot:

- Hikari = `2 × CPUs` (8–32) unless `HIKARI_MAX_POOL` is set — two Postgres connections per core; cap 32 so we do not eat `max_connections=100`.
- Tomcat max = `16 × CPUs` (50–200) unless `SERVER_TOMCAT_THREADS_MAX` is set — waiters on JDBC, Spring’s 200 cap.
- Claim Batch = `max(CPUs, 18)` (1–50). **18** = `500_000 / 28_800 × 2 × 0.5s`. Cap 50 so a poll never loads the full due set.

This laptop (measured 2026-09-21): i5-13450HX, 10 cores / 16 threads, 16 GB. Docker Desktop: 16 CPUs, 7.57 GiB — that VM is why Compose pins memory at all. Postgres 2 CPU / 2 GiB, RabbitMQ 1 / 1 GiB, Redis 0.5 / 256 MiB, app 4 / 3.5 GiB (`-XX:MaxRAMPercentage=50` so heap follows the container). Startup logs `hardware cpus=… heapMb=… hikari=… tomcatMax=… claimBatch=…`.

The in-process proof is `RequestCapacityTest`: concurrent list GETs + Appointment creates (rate limits off, Testcontainers). Thread count follows `availableProcessors()` so the burst uses the cores that exist, not a hard-coded 16. It asserts zero `5xx` and logs req/s. That is a burst check, not an EC2 soak. Integration also asserts one poll claims two due Reminders (batch, not `LIMIT 1`).

## EC2 (last implementation step)

One EC2, main `docker-compose.yml` **or** a host JVM with managed brokers. Secrets live in **`.env.production`** (gitignored; copy of `.env.example` shape) — not in git.

Typical managed stack for this deploy:

| Role | Service | Notes |
| --- | --- | --- |
| Postgres | Supabase | JDBC `?sslmode=require` |
| Redis | Upstash | `SPRING_DATA_REDIS_SSL_ENABLED=true` + username/password (`rediss`) |
| RabbitMQ | CloudAMQP | Port **5671**, `SPRING_RABBITMQ_SSL_ENABLED=true`, `VIRTUAL_HOST` = user vhost |
| SMTP | Brevo | `APP_NOTIFICATIONS_MODE=smtp` + `SPRING_MAIL_*` |

**Do not** set `SPRING_PROFILES_ACTIVE=dev` on EC2 (no demo seed / local Vehicle-cap override). `APP_JWT_SECRET` must be unique and ≥ 32 bytes. Behind nginx/ALB set `APP_RATE_LIMIT_TRUST_FORWARDED_FOR=true`.

Public host: `https://dealership.greatnerve.com`. Auto-size follows that instance’s CPUs; pin only to override the formula. App JVM stays UTC regardless of region (`us-east-1` vs India); see [time.md](time.md).
