# 0009 JPA for CRUD, native SQL for SKIP LOCKED and the clock

Spring Data JPA maps modules and HTTP CRUD, including **Notification** rows and **outbox insert / mark published**. Native SQL / `JdbcTemplate` only where JPA cannot: Reminder due-time `INSERT … SELECT` (`scheduled_at - interval`), send-window expire, no-show `UPDATE`, `FOR UPDATE SKIP LOCKED` claim (Reminders **and** outbox drain). Do not load full graphs to subtract hours. Flyway owns schema; `spring.jpa.hibernate.ddl-auto=validate`.

**Status:** accepted
