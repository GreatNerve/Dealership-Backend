# Appointment (PRD)

## Stories

1. As a Customer, I want to create an Appointment for one of my Vehicles at a Dealership.
2. As a Staff Member, I want to create an Appointment for a Customer’s Vehicle at my home Dealership. I look up `customerId` and `vehicleId` from Customer search, or I create that Customer and Vehicle first.
3. As a caller, I want `Idempotency-Key` on create, so that a timeout retry does not create a second Appointment.
4. As a caller, I want a Mock Appointment (`notify: false`), so that I can seed without sending mail (file log instead).
5. As a Customer, I want to cancel my own Confirmed Appointment, so that pending Reminders are not sent.
6. As a Customer, I want to reschedule my own Confirmed Appointment, so that old Reminders die and new ones are created.
7. As a Customer, I want a Vehicle freed 1 hour after `scheduledAt` if still Confirmed, so that a no-show does not lock the car.
8. As a Customer, I want to GET my own Appointments (by id and paginated, searchable list) using the **Booking Offset** I sent on `scheduledAt`, with Customer, Vehicle, and Dealership nested (not ids only).
9. As a Staff Member, I want to GET Appointments at my home Dealership only (paginated, searchable list) in **Dealership Timezone**, with the same nested Customer, Vehicle, and Dealership.
10. As a Staff Member, I want to GET that Appointment’s Reminder **history** (all Schedule Versions) and nested Notifications (offset, send time, status, failure reason), so reschedule does not hide prior mail. The UI lists **this visit** then **previous booking**, not version numbers.
11. As a Staff Member, I want Instant `from`/`to` on the Appointment list (`scheduled_at`) plus `status`, so the frontend can filter “today” in **Dealership Timezone**.
12. As a Staff Member, I want to mark a Confirmed Appointment **Completed** when the visit is done, so the Vehicle is free and unsent Reminders stop.
13. As a Staff Member, I want to send a **Manual** Notification from that Appointment (`POST /appointments/{id}/notifications`).

## Policies

| Topic | v1 rule |
| --- | --- |
| Blocking cap | Default on: at most one Confirmed Appointment per Vehicle (`APP_ONE_CONFIRMED_PER_VEHICLE=true`), HTTP 409 otherwise. `false` allows many Confirmed on the same Vehicle. Proof is still the partial unique index (`one_confirmed`). |
| Customer create body | `{ vehicleId, dealershipId, scheduledAt, notify? }` |
| Staff create body | `{ customerId, vehicleId, scheduledAt, notify? }`. Dealership from token. |
| Past `scheduledAt` | Reject create. |
| Late vs Reminder windows | At create/reschedule: insert uses the send-window midpoint. Never SENT and still before midpoint → `PENDING` (first book at T−20h still gets 24h). Past midpoint → `EXPIRED` (T−10h, no 24h). Skip that offset again only when it was already SENT and the new visit is closer than the offset (reschedule inside 24h after they got the 24h). See [reminder.md](reminder.md). |
| Cancel / reschedule | Only from Confirmed. Customer: own visits. Staff: home Dealership. Other customer / other shop → 404. |
| Reschedule | New `scheduledAt` must be **in the future**, must **differ** from the current Instant (`400 SCHEDULED_AT_UNCHANGED` if same), and the visit must not already be past (`400 SCHEDULED_AT_PAST`). Cancels unsent Reminders; new rows use `MAX(schedule_version)+1`. Same late-offset rule as create. |
| Complete | Staff, home Dealership, Confirmed only. Not the same as cancel. Frees the Vehicle; unsent Reminders cancelled. Customer → 403. Other shop → 404. |
| Already sent | Cannot unsend. History stays. Appointment Reminder GET keeps prior Schedule Versions. |
| Time | ISO-8601 with offset in (`scheduledAt`). Store UTC Instant **and** **Booking Offset**. No Customer timezone field. Mail / Customer GET: that offset (`10:00 PM UTC+05:30`). Staff GET: **Dealership Timezone**. JVM UTC so EC2 region does not matter. See [../trd/time.md](../trd/time.md). |
| Statuses v1 | `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW_EXPIRED`. **In Progress** is later. |
| Reads | Customer: own Appointments/Vehicles. Staff: Appointments at home Dealership. Staff Customer directory: search, create Customer + Vehicle, then book. Else **404**. List GETs are **paginated and searchable** (`q`). Staff Appointment list also takes Instant `from`/`to` on `scheduled_at` (frontend “today”). |
| Idempotency Key | Required on create. Scoped to the calling User. Same key + body replays for 24h. After TTL, reuse is a new create. Expired `idempotency_keys` are deleted at UTC midnight so unused rows do not accumulate. |

Shop-floor **In Progress** is later ([scope.md](scope.md)). **Completed** is v1 (`POST /appointments/{id}/complete`, Staff).

**Manual** mail from an Appointment: [notification.md](notification.md). Frontend hydrates templates from this GET; do not add a timezone or template field to the Appointment.
