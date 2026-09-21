DROP INDEX reminders_due_scheduled_at;
CREATE INDEX reminders_due_scheduled_at
  ON reminders (scheduled_at)
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING');

DROP INDEX outbox_events_pending;
CREATE INDEX outbox_events_pending
  ON outbox_events (created_at)
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING');
