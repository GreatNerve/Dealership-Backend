# Comments (TRD)

When we write Java (only after the user asks), comments stay **rare**.

## Write a comment only when it explains a non-obvious **why**

Useful:

- Why SKIP LOCKED + lease (two workers, crash recovery). A live `mail-*` owner blocks a second SMTP send; poller/replay locks are handed off. **Manual** lease lives on `notifications`; **System** lease lives on `reminders`.
- Why dead-letter replay sets Reminder `PROCESSING` with a live lease (MailWorker will not send a `DEAD_LETTER` row).
- Why the Claim Batch floor is 18 (`500_000/28_800×2×0.5s` = 10× assignment 50k on an 8-hour day; auto from CPUs). Cap 50 so a poll never loads the full due set. Not “poll 10×”. Full why: [../decision/scale.md](../decision/scale.md).
- Why SMTP timeout is shorter than the lease (slow mail must not double-send).
- Why Notification/outbox is not created at Appointment create.
- Why Manual Notification has `reminder_id` null (it is not “is it time?”).
- Why Delivery Events are append-only (opened/bounce must not overwrite worker SENT/DEAD_LETTER).
- Why bounce/open stats buckets use `min(occurred_at)` in range (so daily bars sum to the distinct headline).
- Why a webhook JSON array is capped at 100 (one transaction; a valid secret must not hold the pool).
- Why Brevo `ts_epoch` ≥ 1e12 is milliseconds (`Instant.ofEpochMilli`); seconds stay below that until year 33658. Storing millis as seconds yields year ~58699.
- Why a long `provider_event_id` is SHA-256 hex (`Inputs.fit`) instead of a 255-char prefix (unique index; prefixes collide).
- Why SMTP Correlation Key is the Notification UUID in a provider-mapped header (schema must not store `X-Mailin-custom`).
- Why a unique index is the proof, not an `if`.
- Why contact / **Vehicle Number** must not appear in logs.
- Why Booking Offset is stored on the Appointment (EC2 us-east must not format India mail in Eastern).
- Why due times / no-show / Send Window midpoint are SQL (adjacent gap ÷ 2; do not hydrate full graphs).
- Why Idempotency Key purge cron is UTC midnight (EC2 host zone must not pick local midnight).
- Why JSON strings use a Jackson deserializer and query/form/header strings use `@InitBinder` (two HTTP pipelines, one `Inputs`). Manual `body` uses `Inputs.multiline` because `\n` is ISO control and the default sanitize collapsed mail to one line. The type-level String deserializer is contextual: property `body` (HTTP Manual send **and** outbox/Rabbit `MailSnapshot`) keeps LF; other JSON strings still strip it.
- Why security 401/403 write JSON in the filter (that path never reaches `GlobalExceptionHandler`).
- Why CSP allows `'unsafe-inline'` script/style (springdoc Swagger UI).
- Why the rate-limit Redis key includes method + path with UUID segments collapsed (per endpoint, not one global IP/user bucket; ids must not split `GET /appointments/{id}`).
- Why Appointment list enrichment is `findAllById` after the page, not `JOIN FETCH` (entities store UUID FKs; `JOIN FETCH` + `Page` is the Hibernate cartesian trap). The Customer `JOIN` on `vehicles.customer_id` is ownership in SQL, not a fetch of the nested JSON.

## Do not comment

- What the next line obviously does (`// get appointments`).
- Change markers (`// added swagger`).
- Section banners (`// ----- helpers -----`).
- Restating the method name.
- Javadoc on every getter or record accessor.

No Lombok. Names should carry the what; comments carry the why when the why is not in the name.

Reuse and layout: [code-style.md](code-style.md).
