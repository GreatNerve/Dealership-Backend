# Deps Compose vs app image

Runtime shape:

- `docker-compose.yml` — **dependencies only** (Postgres, RabbitMQ, Redis, Mailhog). I run the app with Maven.
- `Dockerfile` — **the application image**.
- `docker-compose.app.yml` — deps + app when I want the full stack.

The app is not stuffed into the deps file. Deploy (EC2) is a later step using the full Compose file; it is not an architecture chapter of its own.
