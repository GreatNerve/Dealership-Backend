# Docker (TRD)

Why the split and the caps: [../decision/docker-runtime.md](../decision/docker-runtime.md), [../decision/scale.md](../decision/scale.md). Docker Desktop here is 16 CPU / 7.57 GiB; the numbers below are a partition of **that**, not a generic production SKU.

- `Dockerfile`: multi-stage Maven build → JRE 21 image, non-root, `java -jar`, `-XX:MaxRAMPercentage=50` so heap follows the container (no guessed `-Xmx`). One image, the application.
- `docker-compose.deps.yml`: Postgres 16, RabbitMQ, Redis. Named volumes. **No app.** Postgres 2 CPU / 2 GiB (`shared_buffers=256MB`, `shm_size=256m`) because SKIP LOCKED lives there. RabbitMQ 1 / 1 GiB. Redis 0.5 / 256 MiB (Bucket4j only). Host Postgres is `5432` when the local Windows service is stopped. Use this with `./mvnw spring-boot:run`.
- `docker-compose.yml`: **main** file. Includes deps **and** the app on 8080 (`env_file .env`), app **4 CPU / 3.5 GiB** so `availableProcessors()` inside the container is 4 and Hikari auto-sizes to 8. Mounts `./logs` to `/app/logs` for `notify: false`. `docker compose up --build`. Deploy later on EC2 with this file. SMTP is **Brevo** from `.env`; no Mailhog.

Production public host: `https://dealership.greatnerve.com` (OpenAPI server). CORS allows **all origins** (`APP_CORS_ORIGINS=*`). Do not set `SPRING_PROFILES_ACTIVE=dev` on the production host — that profile seeds demo users (Flyway V900) and defaults the one-Confirmed-per-Vehicle cap off. `make run` defaults to `dev` for local Maven. Production must set `APP_JWT_SECRET` to a unique value (≥ 32 bytes), not the committed default.
