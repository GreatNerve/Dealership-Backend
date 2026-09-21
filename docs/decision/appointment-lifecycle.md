# Appointment lifecycle

v1 statuses: **Confirmed**, **Cancelled**, **Completed**, **No-Show Expired**.

If it is still Confirmed at `scheduledAt + 1 hour`, the system expires it so the Vehicle is not stuck. **Cancel and reschedule** are Customer (own) or Staff (home Dealership). Other customer / other shop → 404. **Completed** (`POST /appointments/{id}/complete`) is Staff at the home Dealership marking the visit done — not the same as cancel (void). Customer complete → 403. Both complete and cancel free the Vehicle and cancel unsent Reminders. **In Progress** / a check-in board is still later.
