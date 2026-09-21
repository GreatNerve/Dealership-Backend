# Operations (PRD)

## Stories

1. As a caller, I want HTTP rate limits **per endpoint** (not one global bucket) with remaining/reset headers, so that clients can back off and a busy list GET does not block create.
2. As a reviewer, I want **15 requests / 60 seconds** per endpoint (never a longer window), so Swagger clicks are not a friction point.
3. As a tester, I want rate limits off in the test profile.
4. As a reviewer, I want Swagger/OpenAPI UI (springdoc, like FastAPI `/docs`) with Authorize email/password (not paste-token only) so I can try `POST /appointments` without a separate client.
5. As a reviewer, I want structured logs with Appointment/Reminder/Notification ids.
6. As a developer, I want a deps Compose file, so I can run the app from Maven (`make run` defaults to the `dev` profile).
7. As a developer, I want the **main** Compose file to run deps **and** the application image together.
8. As a caller, I want list GET endpoints paginated (`page`, `size`, default **100**) and searchable (`q`), so that large shops do not dump every row.
9. As a Customer, I want Reminder mail to show the local time I booked (`10:00 PM`), not UTC and not the EC2 clock.
10. As an operator, I want the public API at `https://dealership.greatnerve.com`.
11. As an operator, I want `/actuator/prometheus` behind JWT so I can scrape send/create counters without opening metrics to the internet.
12. As a browser client, I want CORS to allow **any origin** (`APP_CORS_ORIGINS=*`), so a local or hosted UI can call the API without a whitelist.

## Video success

1. Create an Appointment (`bash scripts/test-appointment.sh <url>`, [manual curl](../../manual-appointment.md), or Swagger).
2. Show logs: create, Reminder rows, send (stub, `logs/notifications.log` when `notify: false`, or Brevo when `smtp`).
3. Show database rows for Appointment and Reminders/Notifications.
4. Optionally: same Idempotency-Key replay; second Vehicle; 409 on same Vehicle.
