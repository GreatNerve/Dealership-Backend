# Capacity and EC2 (TRD)

50k/day × 1.5 = 75k Appointments/day is still ~2 writes/s average. Design for **bursty due-work**: set-based SQL, connection pool, partial indexes, lean outbox snapshots, two app instances sharing SKIP LOCKED. Do not split microservices for this number. Do not load all due rows into the JVM.

The in-process proof is `RequestCapacityTest`: concurrent list GETs + Appointment creates (rate limits off, Testcontainers). It asserts zero `5xx` and logs req/s. That is a burst check, not an EC2 soak.

## EC2 (last implementation step)

Do not expand AWS now. Later: one EC2, main `docker-compose.yml`, `.env` for Brevo/JWT, security group, Actuator. Public host: `https://dealership.greatnerve.com`. Mailhog is local-dev only. Second instance is another week. App JVM stays UTC regardless of region (`us-east-1` vs India); see [time.md](time.md).
