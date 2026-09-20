# 0009 JPA for CRUD, native SQL for SKIP LOCKED and the clock

Spring Data JPA maps modules and HTTP CRUD. Native SQL / `JdbcTemplate` for the clock and the queue: Reminder due-time `INSERT … SELECT` (`scheduled_at - interval`), send-window expire, no-show `UPDATE`, `FOR UPDATE SKIP LOCKED` claim, outbox drain and snapshot payload. Do not load full graphs to subtract hours. Flyway owns schema; `spring.jpa.hibernate.ddl-auto=validate`.

**Status:** accepted
