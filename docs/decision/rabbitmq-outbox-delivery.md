# RabbitMQ delivers due mail via outbox

When a Reminder is **due**, `ReminderService` claims it and writes an **outbox row in that claim transaction**, payload = **lean snapshot** (not a full entity dump). **Manual** send writes `MANUAL_NOTIFICATION` outbox in the same HTTP transaction as the Notification row. Transient Manual SMTP failure marks `RETRY_SCHEDULED`; `NotificationScheduler` claims due Manual rows and writes a new outbox (same backoff as System). A publisher pushes to **RabbitMQ**, then a consumer sends from that snapshot (System and Manual both `ReminderMail`; Manual inner copy is stored body). Appointment create does **not** write outbox rows.

I added the broker on purpose (overkill I can defend): dual-write without an outbox drops or duplicates mail. I did **not** add Kafka. I do not park a 24-hour delay on the queue — the clock stays in Postgres; Rabbit only carries work that is already due (or already composed).
