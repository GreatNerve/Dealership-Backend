# Failure matrix (TRD)

| Failure | Handling |
| --- | --- |
| Client timeout on create | Retry same Idempotency-Key |
| Duplicate create | Original response |
| Same Vehicle Confirmed | 409, unique index |
| DB failure mid-create | Transaction rollback |
| Scheduler down | Overdue rows processed on recovery |
| Worker crash after claim | Reclaim after lease expiry (Reminders and outbox `PROCESSING`) |
| Publish crash after outbox write | Outbox publisher retries; expired `PROCESSING` lease is claimable again |
| Duplicate broker message | Idempotent consumer |
| SMTP timeout / auth (IP, credentials) | Retry same notification key (max 5) |
| Invalid contact (`AddressException`) | DEAD_LETTER immediately |
| Completing after lease lost | `markSent` / `markDead` / `markRetry` no-op if not live `PROCESSING` |
| Max attempts | DEAD_LETTER, metric |
| Cancel vs send race | Documented; possible one extra send |
| Rate limit | 429 + headers |
| DB down on GET | 503 RETRYABLE, not fake empty list |
