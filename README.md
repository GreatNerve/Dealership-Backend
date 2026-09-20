# Dealership Appointment API

Vehicle-service **Appointment** booking and **Reminder** delivery. Modular Spring Boot monolith.

Public host: [https://dealership.greatnerve.com](https://dealership.greatnerve.com)

Docs: [CONTEXT.md](CONTEXT.md), [docs/prd/](docs/prd/), [docs/trd/](docs/trd/), [docs/architecture.md](docs/architecture.md).

## Run locally

Windows PostgreSQL on `5432` must be stopped. Then:

```bash
cp .env.example .env
make hooks
make test
make run
make stop   # free 8080 if a previous JVM is still bound
```

Same without make:

```bash
cp .env.example .env
docker compose -f docker-compose.deps.yml up -d
./mvnw spotless:apply
./mvnw test
./mvnw spring-boot:run
```

Full stack (deps + app image):

```bash
cp .env.example .env
docker compose up -d --build
```

- API: http://localhost:8080/api/v1
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Mailhog (when SMTP): http://localhost:8025

Demo login (`dev` profile): `staff@demo.local` / `password` and `customer@demo.local` / `password`.

Default Notification Mode is **stub**. Set `APP_NOTIFICATIONS_MODE=smtp` to send through Mailhog.
