# Scope

## Not in v1

- Shop-floor **In Progress** (check-in / job started). **Completed** is v1.
- Full privacy matrix (cross-shop history). v1 reads: own / home shop, else 404 — except Staff Customer directory search needed to book.
- Bay/technician double-booking, payments, WhatsApp (Channel stays **EMAIL** in v1), Keycloak/SSO.
- Per-Dealership `maxAdvanceDays` UI (v1 is `APP_MAX_ADVANCE_DAYS` only), split shifts (two intervals per weekday), drag-drop reschedule, walk-in Staff book UI.
- Backend-stored mail templates, global compose without an Appointment, snapshot columns for opened/bounce, Brevo HTTP send API.
- Full analytics (charts, funnels, CSV export, per-offset breakdown). Light `GET /notifications/stats` is v1.
- Kafka, Spring Cloud Gateway, ECS/EKS.
- Per-Customer numeric caps and a shop-wide daily volume cap (rejected). **Service Slot** capacity is v1.
- Extra staff roles; Dealership groups.

## Privacy (later)

Cross-shop Appointment history is later. Staff **may** search Customers and their Vehicles to obtain ids for home-Dealership booking. Appointment reads stay own / home Dealership, otherwise 404.

## Implementation order (same product)

1. **Assignment must-ship:** create Appointment, configured Reminder offsets (default 24h + 2h), due processing, stub send, uniqueness proof, crash/retry/idempotency, tests, **springdoc Swagger UI**, logs, Docker deps. Java: only non-obvious why comments; reuse, no copy-paste ([../trd/code-style.md](../trd/code-style.md)).
2. **Product v1 after the spine:** JWT, Dealership/Vehicle, dual booking, cancel/reschedule (own or home shop), staff complete, no-show, Brevo flag, outbox+RabbitMQ, Bucket4j, replay, Reminder history, shop Notification list, Manual send, Delivery Events + webhook, list Instant filters, light stats.
3. **Another week:** shop-floor, privacy, second app instance, Keycloak, bay/technician assignment.

## Deploy

EC2 is the last implementation step, not a product chapter. See [../trd/capacity-and-ec2.md](../trd/capacity-and-ec2.md).
