# 0013 Service Slot last seat is an advisory lock

A Customer must not take a **Service Slot** after it is full, including two parallel POSTs for the last seat. A unique index on `(dealership_id, scheduled_at)` cannot express `COUNT < capacity`. A `booked` ledger row would drift on cancel, complete, no-show, and reschedule.

**Choice:** `pg_advisory_xact_lock` hashed from Dealership id + slot Instant, then `COUNT(*)` of `CONFIRMED` at that Instant (exclude the row on reschedule), then insert or `409 SLOT_FULL`. Proof is a concurrent Customer test. Redis is not used.

**Status:** accepted
