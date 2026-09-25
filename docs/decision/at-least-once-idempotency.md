# At-least-once claim, at-most-once send

The assignment’s hard line: a Customer must never get the **same Reminder twice**, and it must be **provable**.

**Product lock:** at most **one** stub/SMTP send per Notification idempotency key (one inbox mail for that Reminder Offset + Schedule Version, or one Manual Notification).

Database commit and SMTP cannot be one transaction. Split the guarantees:

| Layer | Guarantee |
| --- | --- |
| Claim / outbox / reclaim | **At-least-once processing** (lease expiry may re-enter the worker) |
| `NotificationSender.send` | **At-most-once** per key once the transport has accepted |

Mechanism (implement when asked):

1. Stable key: System `appointmentId:offsetMinutes:scheduleVersion`; Manual `appointmentId:MANUAL:notificationId`. UNIQUE on `notifications.idempotency_key`.
2. After `NotificationSender.send` returns OK, persist **transport accepted** on that Notification (same key) in a short DB transaction **before** relying on crash recovery.
3. On reclaim / redelivery: if transport already accepted (or status already `SENT`) → **heal** Reminder/Notification to `SENT` only — **do not** call the sender again.
4. Transient SMTP failures (timeout, 4xx) still retry (those never accepted). Permanent failures → `DEAD_LETTER`.
5. SMTP also sets Correlation Key / stable `Message-ID` from the key so a provider-side race during the round-trip does not create a second inbox message when possible.

Unique Reminder `(appointment, offset_minutes, schedule version)`, unique Delivery Event `(notification_id, provider, provider_event_id)`, and Stripe-style HTTP `Idempotency-Key` on create/Manual send stay as today.

**Tests:** crash after provider accept / before durable `SENT` must assert **sender called once**, same key, one Notification row, ends `SENT` (heal path). Concurrent workers still prove one send (`concurrentClaimsSendOnce`).

Older wording that “a retry may hit the provider again” is **withdrawn**.
