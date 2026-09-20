# Folder structure

Documented now. Created when implementing. Do not invent a second layout.

```text
/
  AGENTS.md
  CLAUDE.md
  CONTEXT.md
  README.md                          # later, summarized from docs
  pom.xml
  Dockerfile                         # multi-stage Java 21 app image
  .dockerignore
  docker-compose.yml                 # deps only: postgres, rabbitmq, redis, mailhog
  docker-compose.app.yml             # deps + app
  .cursor/rules/project-constraints.mdc
  docs/
    prd/                             # product modules
    trd/                             # technical modules
    decision/                        # architecture I chose and why
    testing/                         # unit, integration, e2e strategy
    architecture.md
    adr/
  src/main/java/com/dealership/
    shared/                          # page, errors, access 404, time format, lease
    identity/
    dealership/
    customer/
    vehicle/
    appointment/
    reminder/
    notification/
  src/main/resources/
    application.yml
    db/migration/
    db/demo/
  src/test/java/com/dealership/
  no-push/
```

`docker-compose.yml` never starts the app. Local: Compose deps + `./mvnw spring-boot:run`. Full stack: `docker-compose.app.yml`.
