# 0003 At-least-once claim, at-most-once send

Database commit and SMTP/stub I/O cannot be one atomic transaction. Claim, outbox, and lease reclaim stay **at-least-once**. Once `NotificationSender.send` has returned success for a Notification idempotency key, the worker must **not** call the sender again for that key — persist transport-accepted, then heal to `SENT` on reclaim. Transient SMTP failures still retry (max 5); auth / invalid contact → `DEAD_LETTER`. Exactly-once across an in-flight SMTP round-trip still leans on a stable Message-ID / Correlation Key at the provider. API `Idempotency-Key` is a different mechanism for `POST /appointments` and Manual send.

**Status:** accepted (supersedes “retry may hit provider again after accept”)
