# Docker (TRD)

- `docker-compose.yml`: Postgres 16, RabbitMQ, Redis, Mailhog. Named volumes. **No app.**
- `Dockerfile`: multi-stage Maven build → JRE 21 image, non-root, `java -jar`.
- `docker-compose.app.yml`: deps + app, port 8080, `env_file .env`.
