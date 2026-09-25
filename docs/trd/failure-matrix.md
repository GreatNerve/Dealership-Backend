# Failure matrix (TRD)

| Failure | Handling |
| --- | --- |
| Client timeout on create | Retry same Idempotency-Key |
| Duplicate create | Original response |
| Same Vehicle Confirmed | 409, unique index |
| DB failure mid-create | Transaction rollback |
| Scheduler down | Overdue rows processed on recovery |
| Worker crash after claim | Reclaim after lease expiry (Reminders, outbox `PROCESSING`, Manual Notifications) |
| Publish crash after outbox write | Outbox publisher retries; expired `PROCESSING` lease is claimable again |
| Duplicate broker message | Idempotent consumer |
| SMTP timeout / 4xx (`421`, `450`, `451`, `452`, including provider rate limit) | Retry same notification key (max 5) |
| SMTP auth / invalid contact (`AddressException`, `MailParseException`, `SendFailedException` with no reply or a 5xx reply) | DEAD_LETTER immediately |
| Completing after lease lost | `markSent` / `markDead` / `markRetry` no-op if not live `PROCESSING` |
| Max attempts | DEAD_LETTER, metric |
| Cancel vs send race | Documented; possible one extra send |
| Concurrent cancel/complete | 409 `CONCURRENT_UPDATE` (`@Version`) |
| SMTP accepted, crash before `SENT` | Brevo webhook (`request`/`sent`/`delivered` + Correlation Key) heals Notification and Reminder to `SENT` (including a Reminder already `EXPIRED`). A redelivered message does not SMTP; it schedules one retry at lease expiry + 2 minutes. Dead `PROCESSING` past the send-window midpoint becomes `EXPIRED` so it is not stuck. |
| Duplicate webhook | Unique `(notification_id, provider, provider_event_id)`; 200 no second row |
| Webhook unknown Correlation Key | 204; do not insert |
| Provider bounce after SENT | Delivery Event only; worker status unchanged |
| Rate limit | 429 + headers |
| DB down on GET | 503 RETRYABLE, not fake empty list |
