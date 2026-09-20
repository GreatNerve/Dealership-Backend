# 0011 UTC Instant, Booking Offset from scheduledAt

**Context:** A worker on EC2 in us-east must remind a Customer who booked `22:00+05:30`. A separate Customer timezone field is extra. The ISO-8601 value already has the offset.

**Decision:** Persist `timestamptz` Instant. Persist `display_offset` from `scheduledAt` for mail. No Customer timezone field. Reminder due times, send window, and no-show are **set-based PostgreSQL**, not Java `Instant.minus` on loaded graphs. Outbox payload is a lean snapshot for mail. Staff GET uses Dealership Timezone. JVM/Postgres UTC; never the host zone.

**Why SQL for the clock:** one ledger, crash-safe, EC2 region irrelevant. Config still supplies the interval strings (`24h`, `2h`); Postgres subtracts them.

**Why not Instant only:** mail would show `16:30 UTC` instead of the wall clock they picked.

**Why not Customer timezone in the payload:** they already sent the offset.

**Status:** accepted
