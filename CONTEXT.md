# Dealership Appointment Booking

This context is vehicle service booking and reminder delivery for dealerships and their customers. Implementation details live in `docs/trd/` and `docs/adr/`, not here. Architecture decisions live in `docs/decision/`. Testing strategy lives in `docs/testing/`. Java reuse / no-redundancy rules: [docs/trd/code-style.md](docs/trd/code-style.md) (not glossary). Topic → file map: [README.md](README.md).

## Language

### People and shops

**Customer**:
A person who owns one or more **Vehicles** and may book **Appointments**.
_Avoid_: Client, user (when you mean the person, not the login), account

**Dealership**:
A physical service shop that performs work at a **Venue**.
_Avoid_: Dealer (ambiguous with the person), store, location (unless you mean Venue)

**Venue**:
The **Dealership** where an **Appointment** will be carried out.
_Avoid_: Using Venue for a second shop a **Staff Member** belongs to — v1 staff have one home **Dealership**

**Staff Member**:
A logged-in person whose home **Dealership** is the only **Venue** they may book for.
_Avoid_: Employee, advisor, admin (no extra staff roles in v1)

**User**:
A login identity. A **User** is either a **Customer** or a **Staff Member**, not both in v1. Optional **name** (mail greeting when present).
_Avoid_: Account, principal

### Assets and bookings

**Vehicle**:
A car (or similar) owned by exactly one **Customer**, identified by its **Vehicle Number**.
_Avoid_: Car, unit, asset, VIN

**Vehicle Number**:
The registration plate of a **Vehicle** (example `KA01AB1234` or `KA-01-AB-1234`). Unique after sanitize, uppercase, and stripping spaces/hyphens. Not a VIN.
_Avoid_: VIN, chassis number

**Appointment**:
A booked service visit for one **Vehicle** at one **Dealership** at one `scheduledAt`.
_Avoid_: Booking, reservation, slot, job (job is shop-floor, later)

**Blocking Appointment**:
An **Appointment** in status `CONFIRMED`. Default: a **Vehicle** may have at most one (`APP_ONE_CONFIRMED_PER_VEHICLE=true`). Set `false` to allow many Confirmed on the same Vehicle.
_Avoid_: Active booking (say Blocking Appointment), open job

**Creator**:
The **User** who created the **Appointment** (`CUSTOMER` or `DEALERSHIP_STAFF`).
_Avoid_: Owner (the **Customer** owns the **Vehicle**; they may not have created the row)

### Appointment lifecycle (v1)

**Confirmed**:
The **Appointment** is booked and still in the future (or not yet expired). It is a **Blocking Appointment**.
_Avoid_: Scheduled, pending, booked (use Confirmed)

**Cancelled**:
The **Appointment** was voided while **Confirmed** (Customer own, or Staff at the home Dealership). It no longer blocks the **Vehicle**.
_Avoid_: Deleted, void

**No-Show Expired**:
The **Appointment** was still **Confirmed** at `scheduledAt + 1 hour`. The system expired it. The **Vehicle** may be rebooked.
_Avoid_: Missed, expired (say No-Show Expired), completed

**Completed**:
Staff marked the visit done at the home Dealership. It no longer blocks the **Vehicle**. Unsent Reminders are cancelled. Not cancel.
_Avoid_: Done, closed, finished (say Completed)

**In Progress** (later):
Shop-floor “job started”. Not a v1 status.
_Avoid_: Using In Progress in v1 APIs

### Reminders and mail

**Reminder**:
A scheduled intent to notify the **Customer** for one **Appointment**, at each configured offset before `scheduledAt` (default 24 hours and 2 hours).
_Avoid_: Alert, notification (Notification is the delivery record)

**Reminder Offset**:
One configured duration before `scheduledAt` (default `24h` and `2h`). Expand the list in config (`7d`, `6h`, `30m`, …). Stored as `offset_minutes`. Not confirmation in v1.
_Avoid_: Reminder Type, a closed enum of offsets, kind, channel

**Send Window**:
How long a due **Reminder** may still send **if the worker goes down and then recovers**. Adjacent offset gap ÷ 2 (next due, or visit start for the last offset). Worker recovers in the first half: send. Recovers past the midpoint: `EXPIRED`, no mail for that offset. Not a 1-hour buffer around 24h. Not the full stretch to the next offset.
_Avoid_: buffer hour, grace hour (no-show grace is separate)

**Notification**:
The delivery record for one send. **System** Notifications belong to one **Reminder**. **Manual** Notifications belong to an **Appointment** only (`reminder_id` null). Always has `dealership_id`. Channel is **EMAIL** in v1.
_Avoid_: Message, email (email is a Channel), reminder (Reminder is the schedule)

**Channel**:
How the **Notification** is delivered. v1 is **EMAIL** only. Future SMS or push is another Channel, not a second table.
_Avoid_: Notification Mode, provider name (Brevo is an adapter)

**Generation**:
Who created the **Notification**. **SYSTEM** = due **Reminder**. **MANUAL** = Staff compose from an Appointment. Closed enum.
_Avoid_: type, kind, source (unqualified)

**Delivery Event**:
Append-only provider signal on a **Notification** (accepted, delivered, opened, bounced, clicked, spam, blocked, error). Generic enum. Derive “opened?” from events — do not copy status onto `notifications`.
_Avoid_: Brevo event names in the API, snapshot columns (`opened_at`)

**Correlation Key**:
The **Notification** id. SMTP puts it in a provider-mapped custom header (Brevo: `X-Mailin-custom`). Schema and JSON never use that header name. Future Channels map their own header/tag to the same id.
_Avoid_: message-id as the ledger key, storing Brevo field names

**Not Scheduled**:
Staff GET status when that **Reminder** has no **Notification** row yet (not due, or window already skipped). Always return the Notification object; do not omit it or send JSON `null`. Not a stored row. Not a send failure.
_Avoid_: pending (PENDING is a real Notification row), missing, N/A, null notification

**Notification Mode**:
Process-wide `stub` or `smtp`. Stub is the assignment default; SMTP is **Brevo** (`SPRING_MAIL_*` in `.env`). Used when Appointment `notify` is true (system due path) and for **Manual** send.
_Avoid_: Channel as the flag name

**Mock Appointment**:
An **Appointment** created with `notify: false`. Due Reminders append `logs/notifications.log` (ids and wall time, no contact) and store a **Notification** `SENT`. No email. `notify: true` uses **Notification Mode** (stub or SMTP).
_Avoid_: Fake appointment, test appointment (tests are separate), junk

**Mail Replay**:
Re-enqueue of a dead-lettered **Notification** using the same idempotency key. System replay also reopens the **Reminder** to `PROCESSING`. Manual replay has no Reminder.
_Avoid_: Resend (implies a new identity), retry (retry is automatic)

**Delivery Webhook**:
Public HTTP ingest for provider **Delivery Events**. Auth is a shared secret (`APP_DELIVERY_WEBHOOK_SECRET` in `Authorization`: Bearer, Token, or the raw secret), not a User JWT. Adapter maps the payload to the generic enum and finds the **Notification** by **Correlation Key**.
_Avoid_: Brevo-only URL as the product API, mutating worker status from the webhook

**Booking Offset**:
The UTC offset on `scheduledAt` when the **Appointment** is created or rescheduled (example `+05:30`). Stored on the **Appointment**. **Notifications** and Customer GET use it for Local Wall Time. Not a register field.
_Avoid_: Customer timezone in the payload, server timezone, asking for IANA on the Customer

**Dealership Timezone**:
IANA zone on the **Dealership**. Staff GET (including Swagger as staff) shows **Appointment** times in this zone.
_Avoid_: Server timezone, Booking Offset for staff screens

**Local Wall Time**:
`scheduledAt` rendered for humans. Mail and Customer GET: **Booking Offset** on the **Appointment**. Staff GET: **Dealership Timezone**. Stored value is UTC. EC2 region does not change this.
_Avoid_: Treating Local Wall Time as the database value; using the host clock

### Reliability

**Idempotency Key**:
A client-supplied header value that makes `POST /appointments` safe to retry for **that User**. Distinct from notification idempotency. Rows expire after 24h and unused expired rows are deleted at UTC midnight.
_Avoid_: Request id (correlation is different), dedupe token (too vague)

**Schedule Version**:
Integer on **Reminder** rows. Create starts at 1; reschedule inserts `MAX+1` so old **Reminders** cannot collide with new ones. Not a column on **Appointment**.
_Avoid_: Version (unqualified), etag

**Outbox Event**:
A row written in the same database transaction as **Appointment**/**Reminder**/**Notification** state, later published to RabbitMQ. System due work is `REMINDER_DUE`. Staff compose is `MANUAL_NOTIFICATION`.
_Avoid_: Message, event (unqualified)

**Processing Lease**:
A time-bounded claim on a due **Reminder**, **Outbox Event**, or **Manual** Notification send so a crashed worker does not hold it forever.
_Avoid_: Lock (unqualified), mutex

**Claim Batch**:
How many due **Reminders** (and **Outbox Events**) one poll claims with `SKIP LOCKED`. Auto from CPU count (`APP_WORKERS_CLAIM_BATCH=0`). Floor **18** is the 8-hour 500k/day drain (10× the assignment 50k, two offsets, 500ms poll); cap 50 so a tick never loads every due row. Not a fixed 10 and not “poll 10×”. Why: [docs/decision/scale.md](docs/decision/scale.md).
_Avoid_: Fetch size, page size (those are HTTP lists)

### Deferred (do not design yet)

**Privacy** (later):
Who may see whose **Appointment**, **Customer**, and **Vehicle** details beyond v1 RBAC (customer sees self; staff book only their home **Dealership**).
_Avoid_: Inventing row-level sharing rules in v1

## Flagged ambiguities

**User vs Customer**:
**User** is the login. **Customer** is the person who owns **Vehicles**. A **Staff Member** is a **User** who is not a **Customer**.

**Reminder vs Notification**:
**Reminder** answers “is it time?” **Notification** answers “did we deliver?” A **Manual** Notification has no Reminder. **Delivery Events** answer “what did the provider report?” (opened, bounce) without changing worker status.

**Active**:
Do not use. Say **Blocking Appointment** (`CONFIRMED`) or “HTTP rate limit still has tokens.”

**Dealership vs Venue**:
Same shop in v1. Use **Dealership** in APIs. Use **Venue** only in speech for “the place the customer asked to go.”

**Booking**:
Do not use in APIs. The resource is **Appointment**.

**User timezone**:
Do not use. Do not collect a Customer timezone field. Mail uses **Booking Offset** from `scheduledAt`. Staff GET uses **Dealership Timezone**. The EC2 host zone is irrelevant.

## Example dialogue

Dev: A customer has two cars. Can they have two Confirmed Appointments?

Expert: Yes. The default cap is one Blocking Appointment per Vehicle, not per Customer. `APP_ONE_CONFIRMED_PER_VEHICLE=false` turns that cap off.

Dev: Staff at shop A says the customer wants shop B. Can staff create that Appointment?

Expert: No. Staff may only book their home Dealership. The Customer books the other Venue themselves.

Dev: The car’s Appointment time passed an hour ago and nobody cancelled. Can they book again?

Expert: Yes. That Appointment is No-Show Expired, so it is not blocking. They can create a new Confirmed Appointment for the same Vehicle.

Dev: We retried POST /appointments after a timeout. Two Reminder rows?

Expert: No. Same Idempotency Key and body returns the original Appointment. Unique (appointment, Reminder Offset minutes, Schedule Version) still holds.

Dev: The stub ran, then we switched to SMTP and hit replay. Two emails?

Expert: Mail Replay must use the same notification idempotency key. Replay reopens the Reminder to `PROCESSING` so the worker actually sends. At-least-once processing; the provider key is how we avoid two real mails.

Dev: They booked 10:00 PM with offset +05:30. Worker is on EC2 in us-east. What does the Reminder mail say?

Expert: 10:00 PM (UTC+05:30), from the Booking Offset stored on that Appointment. Not 16:30 UTC, not US Eastern. Staff GET still shows Dealership Timezone.

Dev: Staff rescheduled. Did we lose the old 24h Notification that already SENT?

Expert: No. Unsent Reminders are CANCELLED; SENT rows stay. Appointment Reminder GET returns every Schedule Version. The shop Notification list only shows rows that exist — cancelled-never-sent offsets stay on the Appointment timeline.

Dev: Staff send a “visit us again” mail from the Appointment. Is that a Reminder?

Expert: No. Generation MANUAL, Channel EMAIL, reminder_id null, dealership_id set. Frontend hydrates the template; API stores subject and body and enqueues the same outbox path.

Dev: Brevo said the Customer opened the mail. Do we flip Notification to OPENED?

Expert: No. Worker status stays SENT. A Delivery Event OPENED is appended. List and stats derive opened from that log.
