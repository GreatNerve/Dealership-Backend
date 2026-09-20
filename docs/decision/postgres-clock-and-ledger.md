# Postgres is the clock and the ledger

Appointments, Reminders, uniqueness, and the 24h/2h schedule live in **PostgreSQL**. After a crash, due work is any row with `scheduled_at <= now()`, not an in-memory timer and not a 24-hour RabbitMQ TTL. Reminder due times are `appointment.scheduled_at - CAST(:offset AS interval)` in **set-based SQL**. The app does not load full graphs to subtract hours. **Booking Offset** is display only. EC2 region is not the clock.

If the row is not in the database, it did not happen. Redis and the broker are not allowed to be a second ledger.
