# Testing strategy

How we prove the service works. Implementation comes later; this folder is the plan.

Front door: [../../README.md](../../README.md). Terms: [CONTEXT.md](../../CONTEXT.md). Product: [../prd/README.md](../prd/README.md). Tech: [../trd/README.md](../trd/README.md).

| Layer | File | What it proves |
| --- | --- | --- |
| Rules | [principles.md](principles.md) | Behaviour, not internals. Stub is observable. Rate limits off by default. |
| Unit | [unit.md](unit.md) | Parse/format, fingerprints, backoff, one-per-Vehicle policy. Clock SQL is integration. |
| Integration | [integration.md](integration.md) | Postgres constraints, Flyway, idempotency rows, cancel/reschedule/complete, no-show job. |
| End-to-end | [end-to-end.md](end-to-end.md) | HTTP → Appointment → Reminders → due work → stub send → DB. JUnit under `src/test/java/com/dealership/e2e/`. |
| Uniqueness | [uniqueness-and-concurrency.md](uniqueness-and-concurrency.md) | Assignment hard line: never the same Reminder twice, under two workers. |
| Harness | [harness.md](harness.md) | Recording stub, Testcontainers, clocks, seed, profiles. |
| Report | [report.md](report.md) → [../../test-report.md](../../test-report.md) | Root run report: unit / integration / e2e inventory + last Surefire counts. |
| Manual | [../../manual-appointment.md](../../manual-appointment.md) | Curl / `bash scripts/test-appointment.sh <url>` for 24h and 2h Appointments (local or prod). |

**Must pass before the video:** uniqueness + one happy-path e2e (create, Reminder rows for configured offsets, stub invoked once per offset). Capacity burst is extra: it prints how many HTTP requests the in-process app handled.

Commits that touch `src/` or `pom.xml` run Spotless + `./mvnw test` via `.githooks/pre-commit` (`make hooks` once). Docs-only commits skip that.
