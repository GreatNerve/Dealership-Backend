# 0002 Postgres schedules, RabbitMQ delivers

Reminder offsets are config (default 24h/2h) and clocked as rows (`scheduled_at`). Broker TTLs are a bad 24-hour clock. Due work is claimed in PostgreSQL (`FOR UPDATE SKIP LOCKED` + lease), then an **Outbox Event** is written in **that claim transaction** (not at Appointment create), then published to RabbitMQ. **2–8** consumers (default 2), prefetch 1. Redis is not on this path.

**Status:** accepted
