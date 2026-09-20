# Comments (TRD)

When we write Java (only after the user asks), comments stay **rare**.

## Write a comment only when it explains a non-obvious **why**

Useful:

- Why SKIP LOCKED + lease (two workers, crash recovery).
- Why SMTP timeout is shorter than the lease (slow mail must not double-send).
- Why Notification/outbox is not created at Appointment create.
- Why a unique index is the proof, not an `if`.
- Why contact/VIN must not appear in logs.
- Why Booking Offset is stored on the Appointment (EC2 us-east must not format India mail in Eastern).
- Why due times / no-show are SQL (do not hydrate full graphs to subtract hours).

## Do not comment

- What the next line obviously does (`// get appointments`).
- Change markers (`// added swagger`).
- Section banners (`// ----- helpers -----`).
- Restating the method name.
- Javadoc on every getter or record accessor.

No Lombok. Names should carry the what; comments carry the why when the why is not in the name.

Reuse and layout: [code-style.md](code-style.md).
