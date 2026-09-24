ALTER TABLE notifications
  ADD COLUMN locked_by varchar(64),
  ADD COLUMN lease_expires_at timestamptz;

CREATE INDEX notifications_manual_retry
  ON notifications (next_attempt_at)
  WHERE generation = 'MANUAL'
    AND status IN ('RETRY_SCHEDULED', 'PROCESSING');
