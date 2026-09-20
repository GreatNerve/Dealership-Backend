# Failure matrix (TRD)

| Failure | Handling |
| --- | --- |
| Client timeout on create | Retry same Idempotency-Key |
| Duplicate create | Original response |
| Same Vehicle Confirmed | 409, unique index |
| DB failure mid-create | Transaction rollback |
| Scheduler down | Overdue rows processed on recovery |
| Worker crash after claim | Reclaim after lease expiry |
| Publish crash after outbox write | Outbox publisher retries |
| Duplicate broker message | Idempotent consumer |
| SMTP timeout | Retry same notification key |
| Invalid contact | DEAD_LETTER |
| Max attempts | DEAD_LETTER, metric |
| Cancel vs send race | Documented; possible one extra send |
| Rate limit | 429 + headers |
| DB down on GET | 503 RETRYABLE, not fake empty list |
