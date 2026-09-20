# Docker (TRD)

- `Dockerfile`: multi-stage Maven build → JRE 21 image, non-root, `java -jar`. One image, the application.
- `docker-compose.deps.yml`: Postgres 16, RabbitMQ, Redis, Mailhog. Named volumes. **No app.** Host Postgres is `5432` when the local Windows service is stopped. Use this with `./mvnw spring-boot:run`.
- `docker-compose.yml`: **main** file. Includes deps **and** the app on 8080 (`env_file .env`). Mounts `./logs` to `/app/logs` for `notify: false`. `docker compose up --build`. Deploy later on EC2 with this file; Mailhog is local-dev only.

Production public host: `https://dealership.greatnerve.com` (CORS + OpenAPI server).
