# One Confirmed Appointment per Vehicle

Not “10 per person.” Not “100 per shop” (that fights ~100 bookings per dealer per day in the brief).

A Vehicle may have **one Confirmed Appointment** when `APP_ONE_CONFIRMED_PER_VEHICLE=true` (default). Three cars, three bookings. Same car, 409. Enforced by a partial unique index on `vehicle_id` where status is `CONFIRMED` **and** `one_confirmed` — same class of proof as duplicate Reminders. Set the env `false` to store `one_confirmed=false` so that index does not apply (many Confirmed on one Vehicle). Cancelled and No-Show Expired do not block a rebook.
