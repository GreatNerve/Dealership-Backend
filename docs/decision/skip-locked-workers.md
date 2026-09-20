# SKIP LOCKED workers and leases

Due Reminders and outbox rows are claimed with `SELECT ... FOR UPDATE SKIP LOCKED`, a short **lease**, then I/O **outside** the transaction. Send-window and Confirmed filters are in the claim SQL so Java never loads a row only to expire it.

**Worker pool: 2–4** (default **2**, configurable). Email is slow, so more than one worker can send at once. Each consumer `prefetch=1`. Uniqueness still comes from the DB key. Mail body comes from the outbox snapshot, not a second full-graph load.

**Worker pool: 2–4** (default **2**, configurable). Email is slow, so more than one worker can send at once. Each consumer `prefetch=1`. Uniqueness still comes from the DB key.

**Lease: 30 seconds** (`app.workers.lease`), claimed in one SKIP LOCKED transaction — two workers cannot hold the same row. While SMTP is in progress the worker **renews the lease** (heartbeat) so a slow send does not look like a dead worker. SMTP client timeout is **shorter than the lease** (e.g. 20s). Before calling SMTP, and before marking SENT, the worker checks it still owns the lease. If it lost the lease, it must not send or complete.

If a worker dies (heartbeat stops), another takes the row after expiry and retries with the **same** notification idempotency key.
