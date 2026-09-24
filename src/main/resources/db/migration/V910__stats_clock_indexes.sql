CREATE INDEX notifications_dealership_sent_at
  ON notifications (dealership_id, sent_at)
  WHERE status = 'SENT' AND sent_at IS NOT NULL;

CREATE INDEX notifications_dealership_dead_updated
  ON notifications (dealership_id, updated_at)
  WHERE status = 'DEAD_LETTER';

DROP INDEX IF EXISTS notification_delivery_events_type_occurred;
CREATE INDEX notification_delivery_events_type_occurred
  ON notification_delivery_events (event_type, occurred_at, notification_id);
