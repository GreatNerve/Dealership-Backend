# 0006 One Confirmed Appointment per Vehicle

Abuse control is a partial unique index on `vehicle_id` where status is `CONFIRMED`, not a per-Customer count or per-Dealership quota. A Customer with three Vehicles may have three Confirmed Appointments. No-Show Expired and Cancelled do not block. Staff cannot evade this by booking at another shop: the constraint is global on the Vehicle.

**Status:** accepted
