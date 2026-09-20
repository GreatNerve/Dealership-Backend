# Scope

## Not in v1

- Shop-floor **In Progress** / **Completed**.
- Full privacy matrix (cross-shop history). v1 reads: own / home shop, else 404 — except Staff Customer directory search needed to book.
- Slot calendars, bay/technician double-booking, payments, WhatsApp, Keycloak/SSO.
- Kafka, Spring Cloud Gateway, ECS/EKS.
- Per-Customer numeric caps and per-Dealership volume caps (rejected).
- Extra staff roles; Dealership groups.

## Privacy (later)

Cross-shop Appointment history is later. Staff **may** search Customers and their Vehicles to obtain ids for home-Dealership booking. Appointment reads stay own / home Dealership, otherwise 404.

## Implementation order (same product)

1. **Assignment must-ship:** create Appointment, configured Reminder offsets (default 24h + 2h), due processing, stub send, uniqueness proof, crash/retry/idempotency, tests, **springdoc Swagger UI**, logs, Docker deps. Java: only non-obvious why comments; reuse, no copy-paste ([../trd/code-style.md](../trd/code-style.md)).
2. **Product v1 after the spine:** JWT, Dealership/Vehicle, dual booking, cancel/reschedule, no-show, Brevo flag, outbox+RabbitMQ, Bucket4j, replay.
3. **Another week:** shop-floor, privacy, second app instance, Keycloak, slot inventory.

## Deploy

EC2 is the last implementation step, not a product chapter. See [../trd/capacity-and-ec2.md](../trd/capacity-and-ec2.md).
