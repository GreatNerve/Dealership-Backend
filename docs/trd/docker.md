# Docker (TRD)

- `Dockerfile`: multi-stage Maven build → JRE 21 image, non-root, `java -jar`. One image, the application.
- `docker-compose.deps.yml`: Postgres 16, RabbitMQ, Redis. Named volumes. **No app.** Host Postgres is `5432` when the local Windows service is stopped. Use this with `./mvnw spring-boot:run`.
- `docker-compose.yml`: **main** file. Includes deps **and** the app on 8080 (`env_file .env`). Mounts `./logs` to `/app/logs` for `notify: false`. `docker compose up --build`. Deploy later on EC2 with this file. SMTP is **Brevo** from `.env`; no Mailhog.

Production public host: `https://dealership.greatnerve.com` (OpenAPI server). CORS allows **all origins** (`APP_CORS_ORIGINS=*`). Do not set `SPRING_PROFILES_ACTIVE=dev` on the production host — that profile seeds demo users (Flyway V900) and defaults the one-Confirmed-per-Vehicle cap off. `make run` defaults to `dev` for local Maven. Production must set `APP_JWT_SECRET` to a unique value (≥ 32 bytes), not the committed default.
