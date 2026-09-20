# Comments (TRD)

When we write Java (only after the user asks), comments stay **rare**.

## Write a comment only when it explains a non-obvious **why**

Useful:

- Why SKIP LOCKED + lease (two workers, crash recovery).
- Why SMTP timeout is shorter than the lease (slow mail must not double-send).
- Why Notification/outbox is not created at Appointment create.
- Why a unique index is the proof, not an `if`.
- Why contact / **Vehicle Number** must not appear in logs.
- Why Booking Offset is stored on the Appointment (EC2 us-east must not format India mail in Eastern).
- Why due times / no-show / Send Window midpoint are SQL (adjacent gap ÷ 2; do not hydrate full graphs).
- Why Idempotency Key purge cron is UTC midnight (EC2 host zone must not pick local midnight).
- Why JSON strings use a Jackson deserializer and query/form/header strings use `@InitBinder` (two HTTP pipelines, one `Inputs`).
- Why security 401/403 write JSON in the filter (that path never reaches `GlobalExceptionHandler`).
- Why Appointment list enrichment is `findAllById` after the page, not `JOIN FETCH` (entities store UUID FKs; `JOIN FETCH` + `Page` is the Hibernate cartesian trap). The Customer `JOIN` on `vehicles.customer_id` is ownership in SQL, not a fetch of the nested JSON.

## Do not comment

- What the next line obviously does (`// get appointments`).
- Change markers (`// added swagger`).
- Section banners (`// ----- helpers -----`).
- Restating the method name.
- Javadoc on every getter or record accessor.

No Lombok. Names should carry the what; comments carry the why when the why is not in the name.

Reuse and layout: [code-style.md](code-style.md).
