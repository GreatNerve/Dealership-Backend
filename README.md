# Dealership Appointment API

Vehicle-service **Appointment** booking and **Reminder** delivery. Modular Spring Boot monolith.

Public host: [https://dealership.greatnerve.com](https://dealership.greatnerve.com)

Docs: [CONTEXT.md](CONTEXT.md), [docs/prd/](docs/prd/), [docs/trd/](docs/trd/), [docs/architecture.md](docs/architecture.md).

## Reminder send window

Adjacent gap ÷ 2. Remaining time to the next due **greater than** half the gap → send. Remaining **≤ half** → no mail for that offset (`EXPIRED`). Visit at **T**. Default `24h,2h`.

| Reminder | Due | Gap to next | Midpoint | Notification may send | After that |
| --- | --- | --- | --- | --- | --- |
| 24h | T−24h | 22h (to 2h) | **T−13h** | **T−24h → T−13h** | `EXPIRED`, no 24h mail |
| 2h | T−2h | 2h (to visit) | **T−1h** | **T−2h → T−1h** | `EXPIRED`, no 2h mail |

Recover 24h at T−22h or T−20h → send. At T−12h → no. Recover 2h at T−90m → send. At T−30m → no. Details: [docs/prd/reminder.md](docs/prd/reminder.md).

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
