# Deps Compose vs app image

Runtime shape:

- `docker-compose.deps.yml` — **dependencies only** (Postgres, RabbitMQ, Redis, Mailhog). I run the app with Maven.
- `Dockerfile` — **the application image**.
- `docker-compose.yml` — **main** file: deps + app.

The app is not stuffed into the deps file. Deploy (EC2) is a later step using the main Compose file; it is not an architecture chapter of its own. Public hostname: `dealership.greatnerve.com`.
