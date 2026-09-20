# Appointment (PRD)

## Stories

1. As a Customer, I want to create an Appointment for one of my Vehicles at a Dealership.
2. As a Staff Member, I want to create an Appointment for a Customer’s Vehicle at my home Dealership.
3. As a caller, I want `Idempotency-Key` on create, so that a timeout retry does not create a second Appointment.
4. As a caller, I want a Mock Appointment (`notify: false`), so that I can seed without sending mail.
5. As a Customer, I want to cancel a Confirmed Appointment, so that pending Reminders are not sent.
6. As a Customer, I want to reschedule a Confirmed Appointment, so that old Reminders die and new ones are created.
7. As a Customer, I want a Vehicle freed 1 hour after `scheduledAt` if still Confirmed, so that a no-show does not lock the car.
8. As a Customer, I want to GET my own Appointments (by id and paginated, searchable list) using the **Booking Offset** I sent on `scheduledAt`.
9. As a Staff Member, I want to GET Appointments at my home Dealership only (paginated, searchable list) in **Dealership Timezone**.

## Policies

| Topic | v1 rule |
| --- | --- |
| Blocking cap | At most one Confirmed Appointment per Vehicle. HTTP 409 otherwise. |
| Customer create body | `{ vehicleId, dealershipId, scheduledAt, notify? }` |
| Staff create body | `{ customerId, vehicleId, scheduledAt, notify? }`. Dealership from token. |
| Past `scheduledAt` | Reject create. |
| Late vs Reminder windows | Window already past → Reminder skipped/`EXPIRED` with reason. Future windows still created. |
| Cancel / reschedule | Only from Confirmed. |
| Already sent | Cannot unsend. History stays. |
| Time | ISO-8601 with offset in (`scheduledAt`). Store UTC Instant **and** **Booking Offset**. No Customer timezone field. Mail / Customer GET: that offset (`10:00 PM UTC+05:30`). Staff GET: **Dealership Timezone**. JVM UTC so EC2 region does not matter. See [../trd/time.md](../trd/time.md). |
| Statuses v1 | `CONFIRMED`, `CANCELLED`, `NO_SHOW_EXPIRED`. |
| Reads | Customer: own Appointments/Vehicles. Staff: Appointments at home Dealership. Else **404**. List GETs are **paginated and searchable** (`q`). |

Shop-floor In Progress / Completed is later ([scope.md](scope.md)).
