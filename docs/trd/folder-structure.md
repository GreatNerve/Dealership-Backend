# Folder structure

Documented now. Created when implementing. Do not invent a second layout.

```text
/
  AGENTS.md
  CLAUDE.md
  CONTEXT.md
  README.md                          # front door: topic → prd / trd / decision
  manual-appointment.md              # curl flow: 24h + 2h Appointment
  Makefile
  scripts/test-appointment.sh        # URL arg: local or prod 24h + 2h Appointment
  .githooks/pre-commit               # format + test; install with make hooks
  .gitignore
  .vscode/settings.json              # format on save (Google Java Format)
  .editorconfig
  pom.xml                            # includes Spotless
  Dockerfile                         # multi-stage Java 21 app image (root)
  .dockerignore
  docker-compose.yml                 # main: includes docker/deps + docker/app
  docker/
    docker-compose.deps.yml          # postgres, rabbitmq, redis
    docker-compose.app.yml           # app container only
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
    notification/                    # Notification + outbox (JPA / SKIP LOCKED claim)
      FileNotificationLog.java       # notify:false → logs/notifications.log
      ReminderMail.java              # HTML + text from snapshot
      smtp/                          # NotificationSender, stub, SMTP, MailWorker
  src/main/resources/
    application.yml
    db/migration/
    db/demo/
  src/test/java/com/dealership/
  no-push/
```

Local Maven: `docker compose -f docker/docker-compose.deps.yml up -d` then `./mvnw spring-boot:run`. App image only: `make app` (deps + `docker/docker-compose.app.yml`). Full stack: `docker compose up --build` (root main includes deps + app).

What each Java type holds: [code-style.md](code-style.md). Packages are **by module** (`appointment`, `notification`). Role is the **filename**. Nested folders are **concerns** (`notification/smtp`), not `controller/` / `service/` layers. Do not nest `notification/notification`.
