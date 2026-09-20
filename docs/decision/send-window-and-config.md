# Send window and configurable offsets

Reminder offsets are **config** (`app.reminders.offsets` / `APP_REMINDER_OFFSETS`, default `24h,2h`). The list can grow (`7d,24h,6h,2h`) without a Java enum or a schema migration. I store each value as `offset_minutes`. Confirmation mail is not v1.

Notification/outbox rows appear **only when due**. A Reminder ten days out must not create a Notification.

Send window: **adjacent gap ÷ 2**, not a buffer hour and not the full stretch to the next offset. `nextDueAt` = next smaller offset due, or visit start. Send while `dueAt <= now() < dueAt + (nextDueAt − dueAt)/2`. Default `24h,2h`: 24h sends **T−24h → T−13h** (half of 22h); 2h sends **T−2h → T−1h** (half of 2h). Remaining to next due greater than half the gap → send; at or past midpoint → `EXPIRED`. Booked at T−25h, down at T−24h, back at T−23h or T−22h → **send 24h**. Back at T−12h → 24h no. 2h recovered at T−90m → send; at T−30m → no. Cancel and reschedule cancel old rows so a worker must re-check and must not send.
