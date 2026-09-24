# SKIP LOCKED workers and leases

Due Reminders and outbox rows are claimed with `SELECT ... FOR UPDATE SKIP LOCKED`, a short **lease**, then I/O **outside** the transaction. Send-window and Confirmed filters are in the claim SQL so Java never loads a row only to expire it. Why native SQL: JPA cannot `SKIP LOCKED` without hydrating the queue; why I/O outside the TX: SMTP must not hold a row lock for 20s.

Each poll takes a **Claim Batch** from **CPU count** (`APP_WORKERS_CLAIM_BATCH=0` auto). **Why a batch at all:** `LIMIT 1` every 500ms is 2/s; 10× the assignment (500k Appointments/day, two offsets, 8-hour day) needs ~35/s. **Why floor 18, not 10:** `500_000 / 28_800 × 2 × 0.5 ≈ 17.4`. 10 was a mistaken “10× the poll,” not 10× the brief. **Why cap 50:** a tick must not `findAll` due work. Same batch on outbox so publish does not lag claim. Arithmetic: [scale.md](scale.md).

**Worker pool: 2–8** (default **2**, configurable). Email is slow, so more than one worker can send at once — but not 16 workers just because the laptop has 16 threads. SMTP (Brevo) is the limiter; uniqueness still comes from the DB key. Each consumer `prefetch=1` so a slow send does not prefetch-and-stall extra messages. Mail body comes from the outbox snapshot, not a second full-graph load.

**Lease: 30 seconds** (`app.workers.lease`), claimed in one SKIP LOCKED transaction — two workers cannot hold the same row. While SMTP is in progress the worker **renews the lease** (heartbeat) so a slow send does not look like a dead worker. A live `mail-*` owner blocks a second send; poller and replay locks are handed off to that mail worker. SMTP client timeout is **shorter than the lease** (e.g. 20s). Before calling SMTP, and before marking SENT, the worker checks it still owns the lease. If it lost the lease, it must not send or complete. Staff replay of `DEAD_LETTER` reopens the Reminder as `PROCESSING` with a live lease so this path can run.

If a worker dies (heartbeat stops), another takes the row after expiry and retries with the **same** notification idempotency key.
