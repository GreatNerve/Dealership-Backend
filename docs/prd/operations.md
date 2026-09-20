# Operations (PRD)

## Stories

1. As a caller, I want HTTP rate limits with remaining/reset headers, so that clients can back off.
2. As a Staff Member, I want a higher HTTP budget than a Customer.
3. As a tester, I want rate limits off in the test profile.
4. As a reviewer, I want Swagger/OpenAPI UI (springdoc, like FastAPI `/docs`) so I can try `POST /appointments` without a separate client.
5. As a reviewer, I want structured logs with Appointment/Reminder/Notification ids.
6. As a developer, I want Docker Compose for dependencies only, so I can run the app from Maven.
7. As a developer, I want an application image, so the full stack can run together later.
8. As a caller, I want list GET endpoints paginated (`page`, `size`) and searchable (`q`), so that large shops do not dump every row.
9. As a Customer, I want Reminder mail to show the Local Wall Time I booked (`10:00 PM UTC+05:30` from `scheduledAt`), not the stored UTC Instant and not the EC2 clock.

## Video success

1. Create an Appointment (curl or Swagger).
2. Show logs: create, Reminder rows, send (stub or Mailhog).
3. Show database rows for Appointment and Reminders/Notifications.
4. Optionally: same Idempotency-Key replay; second Vehicle; 409 on same Vehicle.
