# 0003 At-least-once delivery with idempotent keys

Database commit and SMTP/stub I/O cannot be one atomic transaction. The design is at-least-once processing: persist Notification intent, call `NotificationSender` with a stable key `appointmentId:reminderType:scheduleVersion`, retry transients, dead-letter permanents. Exactly-once external mail is not claimed. API `Idempotency-Key` is a different mechanism for `POST /appointments`.

**Status:** accepted
