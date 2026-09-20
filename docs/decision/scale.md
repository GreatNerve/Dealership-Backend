# Size at 50k × 1.5, burst not Kafka

The brief is ~50,000 Appointments/day. I size at **×1.5** (75k/day). That is still ~2 writes/s.

The real load is **many 24h Reminders becoming due together**. I design **set-based SQL** (due times, expire, no-show — no full-graph load), partial indexes, leases, SKIP LOCKED, lean outbox snapshots, **2–4 mail workers** (default 2), and two app instances sharing the same DB/broker. I do not introduce Kafka or extra services for that number.
