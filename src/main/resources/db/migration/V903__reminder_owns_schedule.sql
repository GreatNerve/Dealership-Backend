ALTER TABLE appointments DROP COLUMN schedule_version;
ALTER TABLE reminders DROP COLUMN locked_at;
ALTER TABLE outbox_events DROP COLUMN locked_at;
