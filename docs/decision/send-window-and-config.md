# Send window and configurable offsets

Reminder offsets are **config** (`app.reminders.offsets`, default 24h and 2h). I do not hardcode durations in Java. Confirmation mail is not v1.

Notification/outbox rows appear **only when due**. A Reminder ten days out must not create a Notification.

Send window: after due, keep sending on recovery until the **next** offset (24h Reminder still sends at T-20h; at T-1h it expires and the 2h Reminder owns the window). Last offset lasts until visit start. Cancel and reschedule cancel old rows so a worker must re-check and must not send.
