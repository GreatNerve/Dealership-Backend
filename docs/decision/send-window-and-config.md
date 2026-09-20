# Send window and configurable offsets

Reminder offsets are **config** (`app.reminders.offsets` / `APP_REMINDER_OFFSETS`, default `24h,2h`). The list can grow (`7d,24h,6h,2h`) without a Java enum or a schema migration. I store each value as `offset_minutes`. Confirmation mail is not v1.

Notification/outbox rows appear **only when due**. A Reminder ten days out must not create a Notification.

Send window: after due, keep sending on recovery until the **next** offset (24h Reminder still sends at T-20h; at T-1h it expires and the 2h Reminder owns the window). Last offset lasts until visit start. Cancel and reschedule cancel old rows so a worker must re-check and must not send.
