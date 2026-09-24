# 0012 SMTP Correlation Key and append-only Delivery Events

Keep **SMTP** as the send path. Brevo (and future providers) see our **Notification** UUID in a mapped custom header (`X-Mailin-custom` in the Brevo adapter only). Schema, JSON, and logs use `notifications.id` — never provider field names. Switching Channel later (SMS) maps a different tag to the same id.

Do **not** add opened/bounced columns on `notifications`. Worker status stays PENDING→SENT/DEAD_LETTER. Provider signals live in `notification_delivery_events` (unique on `notification_id + provider + provider_event_id`). Lists and stats `EXISTS` that log. Duplicating a “current status” would drift from the event history.

Do **not** switch v1 send to Brevo’s HTTP API. SMTP already works for the assignment; webhooks echo `X-Mailin-custom`. HTTP send would be a second pipeline without a stronger uniqueness story.

**Manual** Notifications are Appointment-scoped (`reminder_id` null). Forcing a fake Reminder would mix “is it time?” with staff compose.

**Status:** accepted
