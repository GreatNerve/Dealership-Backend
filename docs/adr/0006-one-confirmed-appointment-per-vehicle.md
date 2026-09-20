# 0006 One Confirmed Appointment per Vehicle

Abuse control is a partial unique index on `vehicle_id` where status is `CONFIRMED` and `one_confirmed`, not a per-Customer count or per-Dealership quota. A Customer with three Vehicles may have three Confirmed Appointments. No-Show Expired and Cancelled do not block. Staff cannot evade this by booking at another shop: the constraint is global on the Vehicle.

`APP_ONE_CONFIRMED_PER_VEHICLE` (default `true`) is the create-time switch: `true` writes `one_confirmed=true` (index applies); `false` writes `one_confirmed=false` (many Confirmed allowed). Proof stays PostgreSQL, not an application `if`.

**Status:** accepted
