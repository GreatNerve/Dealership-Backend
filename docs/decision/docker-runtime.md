# Deps Compose vs app image

Runtime shape:

- `docker-compose.deps.yml` — **dependencies only** (Postgres, RabbitMQ, Redis). I run the app with Maven.
- `Dockerfile` — **the application image**.
- `docker-compose.yml` — **main** file: deps + app.

**Why two Compose files:** `make run` should not rebuild a fat app image to try a Java change. **Why resource caps on deps too:** this Docker Desktop VM is 7.57 GiB / 16 CPUs. Unbounded Postgres + Rabbit + Redis + app fight and swap. Caps are a split of **that** VM (see [scale.md](scale.md)): Postgres 2 CPU / 2 GiB because it is the ledger; Rabbit 1 / 1 GiB because the broker is RAM-hungry; Redis 0.5 / 256 MiB because it only holds rate-limit counters; app 4 / 3.5 GiB with `-XX:MaxRAMPercentage=50` so heap follows the container instead of a guessed `-Xmx`.

The app is not stuffed into the deps file. Deploy (EC2) is a later step using the main Compose file; it is not an architecture chapter of its own. Public hostname: `dealership.greatnerve.com`.
