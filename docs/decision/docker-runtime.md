# Deps Compose vs app image

Runtime shape:

- `Dockerfile` (root) — **the application image**.
- `docker/docker-compose.deps.yml` — **dependencies only** (Postgres, RabbitMQ, Redis). I run the app with Maven.
- `docker/docker-compose.app.yml` — **app container only** (standalone service definition; build uses root `Dockerfile`).
- `docker-compose.yml` (root) — **main** file: includes deps + app.

**Why Compose files under `docker/`:** root stays the deploy entry (`docker compose up`) and the image recipe (`Dockerfile`). Deps and app-only overlays live beside each other without cluttering the root. **Why not one file:** `make run` should not rebuild a fat app image to try a Java change. **Why resource caps on deps too:** this Docker Desktop VM is 7.57 GiB / 16 CPUs. Unbounded Postgres + Rabbit + Redis + app fight and swap. Caps are a split of **that** VM (see [scale.md](scale.md)): Postgres 2 CPU / 2 GiB because it is the ledger; Rabbit 1 / 1 GiB because the broker is RAM-hungry; Redis 0.5 / 256 MiB because it only holds rate-limit counters; app 4 / 3.5 GiB with `-XX:MaxRAMPercentage=50` so heap follows the container instead of a guessed `-Xmx`.

The app is not stuffed into the deps file. Deploy (EC2) is a later step using the main Compose file; it is not an architecture chapter of its own. Public hostname: `dealership.greatnerve.com`.
