# Appointment (PRD)

## Stories

1. As a Customer, I want to create an Appointment for one of my Vehicles at a Dealership.
2. As a Staff Member, I want to create an Appointment for a Customer’s Vehicle at my home Dealership. I look up `customerId` and `vehicleId` from Customer search, or I create that Customer and Vehicle first.
3. As a caller, I want `Idempotency-Key` on create, so that a timeout retry does not create a second Appointment.
4. As a caller, I want a Mock Appointment (`notify: false`), so that I can seed without sending mail (file log instead).
5. As a Customer, I want to cancel a Confirmed Appointment, so that pending Reminders are not sent.
6. As a Customer, I want to reschedule a Confirmed Appointment, so that old Reminders die and new ones are created.
7. As a Customer, I want a Vehicle freed 1 hour after `scheduledAt` if still Confirmed, so that a no-show does not lock the car.
8. As a Customer, I want to GET my own Appointments (by id and paginated, searchable list) using the **Booking Offset** I sent on `scheduledAt`, with Customer, Vehicle, and Dealership nested (not ids only).
9. As a Staff Member, I want to GET Appointments at my home Dealership only (paginated, searchable list) in **Dealership Timezone**, with the same nested Customer, Vehicle, and Dealership.
10. As a Staff Member, I want to GET that Appointment’s Reminders and Notifications (offset, send time, status, failure reason), so I can see whether mail sent or why it failed.

## Policies

| Topic | v1 rule |
| --- | --- |
| Blocking cap | Default on: at most one Confirmed Appointment per Vehicle (`APP_ONE_CONFIRMED_PER_VEHICLE=true`), HTTP 409 otherwise. `false` allows many Confirmed on the same Vehicle. Proof is still the partial unique index (`one_confirmed`). |
| Customer create body | `{ vehicleId, dealershipId, scheduledAt, notify? }` |
| Staff create body | `{ customerId, vehicleId, scheduledAt, notify? }`. Dealership from token. |
| Past `scheduledAt` | Reject create. |
| Late vs Reminder windows | Window already past → Reminder skipped/`EXPIRED` with reason. Future windows still created. |
| Cancel / reschedule | Only from Confirmed. |
| Already sent | Cannot unsend. History stays. |
| Time | ISO-8601 with offset in (`scheduledAt`). Store UTC Instant **and** **Booking Offset**. No Customer timezone field. Mail / Customer GET: that offset (`10:00 PM UTC+05:30`). Staff GET: **Dealership Timezone**. JVM UTC so EC2 region does not matter. See [../trd/time.md](../trd/time.md). |
| Statuses v1 | `CONFIRMED`, `CANCELLED`, `NO_SHOW_EXPIRED`. |
| Reads | Customer: own Appointments/Vehicles. Staff: Appointments at home Dealership. Staff Customer directory: search, create Customer + Vehicle, then book. Else **404**. List GETs are **paginated and searchable** (`q`). |
| Idempotency Key | Required on create. Same key + body replays for 24h. After TTL, reuse is a new create. Expired `idempotency_keys` are deleted at UTC midnight so unused rows do not accumulate. |

Shop-floor In Progress / Completed is later ([scope.md](scope.md)).
