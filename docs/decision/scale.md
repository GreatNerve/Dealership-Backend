# Size at assignment 50k × 10, pools from this machine

## What the brief actually said

The PDF: *“Assume up to 50,000 appointments a day across 500 dealerships.”* That is the only load number in the assignment. It did **not** ask for a 10× poll, a Claim Batch of 10, or Kafka.

I size at **10× that number: 500,000 Appointments/day**, still **500 Dealerships**. Why 10× and not 1.5×: the brief is a take-home ceiling, not a measured production peak. Reviewers will ask “what if it is busier than 50k?” 10× is one order of magnitude — enough to prove the design is not glued to the happy-path average — without pretending we have 5 million bookings or a mesh. Shops stay at 500 because the bottleneck is **Reminder due-work**, not “how many Dealership rows.”

## The load that matters

Creates are cheap. 500k/day is ~5.8 writes/s over 24h, or ~17.4/s if the same volume lands in an 8-hour booking day. Two Reminder offsets (24h and 2h) mean **two due rows per Appointment**. Average due-claim rate ≈ 12/s; 8-hour day ≈ 35/s.

The spike is **many 24h Reminders becoming due in the same minute** (people book around the same local hour). `LIMIT 1` every 500ms is only 2 claims/s — that cannot drain 35/s. Loading every due row into the JVM is the other failure mode the architecture already forbids.

So the poller claims a **bounded batch**, then one `IN` load of mail facts. Outbox drain uses the same batch so publish keeps up with claim. Mail workers stay **2–4**: SMTP is the slow side; extra consumers past 4 do not make Brevo faster and the product already locked that range.

## Why pools follow this process, not a guessed constant

A laptop, Docker Desktop, and a later EC2 box do not have the same CPUs or RAM. Hard-coding Hikari=20, Tomcat=200, Claim Batch=10 is a guess that is wrong on every machine except the one where it was typed.

At boot the JVM reads **this process**: `Runtime.availableProcessors()` and `maxMemory()`. Compose CPU/memory limits change what the container sees; `-XX:MaxRAMPercentage=50` makes heap follow the container instead of a guessed `-Xmx`. Startup logs the resolved numbers (`hardware cpus=… heapMb=… hikari=… tomcatMax=… claimBatch=…`) so a reviewer can check them against the box. Env still wins if you pin (`HIKARI_MAX_POOL`, `SERVER_TOMCAT_THREADS_MAX`, `APP_WORKERS_CLAIM_BATCH`).

Measured on this repo’s machine (2026-09-21): **i5-13450HX, 10 cores / 16 threads, 16 GB RAM**. Docker Desktop VM: **16 CPUs, 7.57 GiB**. Those are facts for this environment, not a generic cloud SKU.

## Formulas (and why those numbers)

| Pool | Formula | Why |
| --- | --- | --- |
| Hikari | `2 × CPUs`, clamp 8–32 | PostgreSQL-on-SSD rule of thumb: about two connections per core. Min 8 so a 1–2 CPU CI box still has room for claim + HTTP. Max 32 so we do not stampede Postgres `max_connections=100` (Compose also runs healthchecks and a superuser). |
| Tomcat max threads | `16 × CPUs`, clamp 50–200 | HTTP is mostly waiting on Postgres. 16 waiters per CPU is a common servlet default; cap **200** is Spring Boot’s own default — more threads on 16 cores just queue on 32 connections. Min 50 so a tiny container still accepts a burst. |
| Claim Batch | `max(CPUs, 18)`, clamp 1–50 | **18** is the 8-hour 500k/day drain, not “10× LIMIT 1”: `500_000 / 28_800s × 2 offsets × 0.5s poll ≈ 17.4` → 18. If CPUs are higher, batch follows cores so SKIP LOCKED work can use them. Cap **50** so one tick never `findAll`s the due set. |

On this 16-thread host: Hikari 32, Tomcat 200, Claim Batch **18**. Inside Compose the app is limited to 4 CPUs: Hikari 8, Tomcat 64, Claim Batch still **18** (the 500k floor is higher than 4). Tests pin Hikari 24 so Testcontainers stays deterministic; Claim Batch still auto (18 here).

Mail / Rabbit prefetch stay 1 per consumer. Uniqueness is the DB key, not a bigger prefetch. **Claim Batch is not mail throughput.** 500k Appointments/day × 2 offsets is ~35 due rows/s; 2–4 workers at prefetch 1 drain as fast as Brevo allows. If SMTP is hundreds of ms, the Send Window holds the queue until midpoint, then unsent rows `EXPIRED`. That cap is the product rule (workers 2–4), not a pool multiplier.

## Why Compose is split this way

Docker Desktop only has **7.57 GiB** for Postgres + RabbitMQ + Redis + the app. Unbounded services fight and the VM swaps. Caps are a partition of **that** RAM, with ~0.8 GiB left for Docker itself:

| Service | CPU | RAM | Why |
| --- | --- | --- | --- |
| Postgres | 2 | 2 GiB (`shared_buffers=256MB`) | Ledger and SKIP LOCKED. ~25% of a 2 GiB box is a safe `shared_buffers` starting point. `shm_size=256m` so parallel query / vacuum does not fail. |
| RabbitMQ | 1 | 1 GiB | Broker is RAM-hungry; 1 GiB is the usual small-node floor. Prefetch 1 keeps the queue, not the JVM, as the buffer. |
| Redis | 0.5 | 256 MiB | Bucket4j counters only. Tiny. |
| App | 4 | 3.5 GiB, heap 50% | HTTP + poller + 2–4 mail threads. `MaxRAMPercentage=50` (~1.7 GiB heap) leaves metaspace and native for Tomcat/JDBC. |

`make run` uses deps Compose (Postgres/Rabbit/Redis capped) and a **host** JVM, which sees 16 threads and ~4 GiB ergonomic heap on this 16 GB laptop. `make up` uses the app container limits above. Pin env on EC2 when that host’s CPU count differs.

## What I am not doing

- **Kafka / extra services** for 500k/day. Average is still tens of writes per second. The spike is SQL + a bounded claim, not a log cluster.
- **Sizing mail workers past 4.** SMTP latency dominates; uniqueness is already in Postgres.
- **`findAll` due Reminders.** Batch is the bound; the next 500ms poll takes the next batch.
- **Copying another machine’s pool sizes into yaml as if they were universal.** Auto at boot; pin only when you mean it.
