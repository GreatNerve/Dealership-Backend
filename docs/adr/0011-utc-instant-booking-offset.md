# 0011 UTC Instant, Booking Offset from scheduledAt

**Context:** A worker on EC2 in us-east must remind a Customer who booked a 10:00 AM **Service Slot** in `Asia/Kolkata`. Slot JSON is a UTC Instant (`04:30Z`). A separate Customer timezone field is extra.

**Decision:** Persist `timestamptz` Instant. Persist `display_offset` from **Dealership Timezone** at that Instant for mail. Payload offset only parses the Instant. No Customer timezone field. Reminder due times, send window, and no-show are **set-based PostgreSQL**, not Java `Instant.minus` on loaded graphs. Outbox payload is a lean snapshot for mail. Staff GET uses Dealership Timezone. JVM/Postgres UTC; never the host zone.

**Why SQL for the clock:** one ledger, crash-safe, EC2 region irrelevant. Config still supplies the interval strings (`24h`, `2h`); Postgres subtracts them.

**Why not Instant only / payload `Z`:** mail would show `4:30 AM` UTC instead of the 10:00 AM slot they picked.

**Why not Customer timezone in the payload:** the shop zone already defines the slot wall clock.

**Status:** accepted
