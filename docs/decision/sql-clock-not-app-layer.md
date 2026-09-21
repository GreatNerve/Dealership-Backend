# SQL clock, not the application layer

Due times, skip/expire, send window, and no-show are **set-based PostgreSQL**. The app does not load full Appointment/Reminder graphs, subtract hours, and save.

After a claim, replicate a **lean snapshot** into the outbox payload (ids, `scheduled_at`, `display_offset`, shop name, vehicle make/model/year + Vehicle Number, contact). The mail worker formats that snapshot. It does not hydrate entities again.

Faster under a due-work burst: one indexed `UPDATE`/`INSERT … SELECT` instead of N round-trips. EC2 region still irrelevant because `timestamptz` + `now()` live in Postgres.
