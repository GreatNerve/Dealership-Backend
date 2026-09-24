.PHONY: help hooks fmt check test run stop deps app up down precommit appointment

MAKEFLAGS += --no-print-directory
# Windows Make resolves ./mvnw to mvnw.cmd but still passes "./mvnw" to cmd → '.' not recognized.
ifeq ($(OS),Windows_NT)
  MVNW := mvnw.cmd
else
  MVNW := ./mvnw
endif
PORT ?= 8080
COMPOSE_DEPS := docker/docker-compose.deps.yml
COMPOSE_APP := docker/docker-compose.app.yml
# Always load root .env for Postgres/Rabbit password substitution (paths in
# docker/*.yml stay relative to those files: ../.env, context ..).
COMPOSE := docker compose --env-file .env

help:
	@echo "hooks      install git pre-commit (format + test)"
	@echo "fmt        Spotless apply"
	@echo "check      Spotless check"
	@echo "test       format then mvnw test (Testcontainers)"
	@echo "deps       docker compose deps only (Postgres, RabbitMQ, Redis)"
	@echo "run        deps + spring-boot:run"
	@echo "app        deps + app image only (docker/docker-compose.app.yml)"
	@echo "stop       kill whatever is listening on PORT (default 8080)"
	@echo "up         full stack via root docker-compose.yml"
	@echo "down       docker compose down"
	@echo "appointment  24h + 2h Appointment vs URL (scripts/test-appointment.sh)"
	@echo "precommit  what the hook runs"

hooks:
	@mkdir -p .git/hooks
	@cp .githooks/pre-commit .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit .githooks/pre-commit
	@echo "pre-commit installed"

fmt:
	$(MVNW) -q spotless:apply

check:
	$(MVNW) -q spotless:check

test: fmt
	$(MVNW) test

precommit:
	$(MVNW) -q spotless:apply
	$(MVNW) test

deps:
	$(COMPOSE) -f $(COMPOSE_DEPS) up -d

run: deps
	set -a && [ -f .env ] && . ./.env; set +a && SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-dev} $(MVNW) spring-boot:run

# App container alone; deps file is merged so postgres/redis/rabbit hostnames resolve.
app: deps
	$(COMPOSE) -f $(COMPOSE_DEPS) -f $(COMPOSE_APP) up -d --build app

stop:
	@pids=""; \
	if command -v lsof >/dev/null 2>&1; then \
	  pids=$$(lsof -ti tcp:$(PORT) 2>/dev/null || true); \
	fi; \
	if [ -z "$$pids" ]; then \
	  pids=$$(netstat -ano 2>/dev/null | grep -E ":$(PORT) .*LISTEN" | awk '{print $$NF}' | sort -u | grep -E '^[0-9]+$$' || true); \
	fi; \
	if [ -z "$$pids" ]; then \
	  echo "port $(PORT) is free"; \
	else \
	  for pid in $$pids; do \
	    taskkill //F //PID $$pid >/dev/null 2>&1 || kill -9 $$pid >/dev/null 2>&1 || true; \
	  done; \
	  echo "freed port $(PORT)"; \
	fi

up:
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

appointment:
	bash scripts/test-appointment.sh http://localhost:8080
