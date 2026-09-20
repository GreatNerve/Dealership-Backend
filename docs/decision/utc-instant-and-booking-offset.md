# UTC Instant plus Booking Offset on the Appointment

Store `scheduled_at` as `timestamptz` (UTC Instant). Also store **Booking Offset** (`display_offset`) taken from `scheduledAt` in the body (`+05:30`) for mail only. Do not collect a Customer timezone.

Clock math is **SQL** (`scheduled_at - CAST(:offset AS interval)`, `scheduled_at <= now()`, `scheduled_at + interval '1 hour'`). Java does not persist `Instant.minus`. EC2 in us-east cannot print US Eastern or compute a different due time. Staff GET uses **Dealership Timezone**.

