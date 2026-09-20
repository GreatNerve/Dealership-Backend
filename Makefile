.PHONY: help hooks fmt check test run stop deps up down precommit appointment

MAKEFLAGS += --no-print-directory
MVNW := ./mvnw
PORT ?= 8080

help:
	@echo "hooks      install git pre-commit (format + test)"
	@echo "fmt        Spotless apply"
	@echo "check      Spotless check"
	@echo "test       format then ./mvnw test (Testcontainers)"
	@echo "deps       docker compose deps only"
	@echo "run        deps + spring-boot:run"
	@echo "stop       kill whatever is listening on PORT (default 8080)"
	@echo "up         full stack docker compose"
	@echo "down       docker compose down"
	@echo "appointment  localhost 24h + 2h Appointment (scripts/test-appointment.sh)"
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
	docker compose -f docker-compose.deps.yml up -d

run: deps
	$(MVNW) spring-boot:run

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
	docker compose up -d --build

down:
	docker compose down

appointment:
	bash scripts/test-appointment.sh
