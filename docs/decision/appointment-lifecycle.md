# Appointment lifecycle (no shop floor)

v1 statuses: **Confirmed**, **Cancelled**, **No-Show Expired**.

If it is still Confirmed at `scheduledAt + 1 hour`, the system expires it so the Vehicle is not stuck. I am **not** building In Progress / Completed or a staff check-in board. That is a workshop product; the assignment is booking and reminders.
