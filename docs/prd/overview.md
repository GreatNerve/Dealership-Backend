# Overview

Dealerships and Customers need to book vehicle service visits and remind the Customer 24 hours and 2 hours before the visit. Retried HTTP calls, worker crashes, and duplicate sends must not produce two of the same Reminder.

The assignment needs a working service, tests, a design write-up, and a demo under five minutes. The product around that spine is a multi-actor booking API (Customer self-book and Staff book-on-behalf), not a shop-floor workshop system.

## Solution

One Spring Boot service:

- Customers register Vehicles and book Appointments at a Dealership.
- Staff Members book Appointments for a Customer at their **home Dealership only**. They search or create that Customer (and Vehicle) to obtain ids. They complete, cancel, and reschedule Confirmed visits at that shop. Customers cancel and reschedule their own visits; they cannot mark a visit Completed.
- Creating an Appointment transactionally creates Reminder rows for each configured offset (default 24h and 2h) unless that send window is already past (or that offset was already SENT and the new due is past).
- A durable scheduler finds due Reminders after downtime. Delivery is stub (default) or Brevo SMTP (flag). Staff may also enqueue a **Manual** Notification from an Appointment. **Email tracking** is provider **Delivery Events** (open, bounce) on a public webhook, append-only — [email-tracking.md](email-tracking.md).
- A Customer never receives the same Reminder twice, proven by database uniqueness and concurrency tests.

## Goals

1. Honour the assignment: `POST /appointments`, configured Reminder offsets (default 24h and 2h), stub sender, provable no duplicate Reminder, tests, README/diagram later, demo video.
2. Make failure modes explicit: retries, leases, dead-letter, replay, no-show expiry, webhook idempotency.
3. Signal production thinking (JWT, RabbitMQ outbox, Redis rate limits, Swagger, Docker) without microservices.
4. Stay defendable line-by-line in review.

**Scale:** the PDF is 50,000 Appointments/day across 500 Dealerships. We size **10× = 500,000/day** as headroom for “what if it is busier,” not because we measured 500k, and not by multiplying the poll by 10. Hikari / Tomcat / Claim Batch follow **this process’s CPU count** so the laptop, Docker, and a later EC2 box are not stuck with one guessed yaml. Why those formulas: [../decision/scale.md](../decision/scale.md).
