# 0003 At-least-once claim, at-most-once send

Database commit and SMTP/stub I/O cannot be one atomic transaction. Claim and outbox stay **at-least-once**. Retry backoff is **2–10 minutes**, and an expired `PROCESSING` lease is not reclaimed for 2 minutes, so a Brevo webhook can arrive. `ACCEPTED` / `DELIVERED` on the Correlation Key heals an open Notification and Reminder to `SENT`; the worker must not send that key again. Transient SMTP failures still retry (max 5); auth / invalid contact → `DEAD_LETTER`. API `Idempotency-Key` is a different mechanism for `POST /appointments` and Manual send.

**Status:** accepted (supersedes “retry may hit provider again after accept”)
