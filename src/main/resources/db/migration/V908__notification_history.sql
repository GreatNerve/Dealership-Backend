CREATE TYPE notification_channel AS ENUM ('EMAIL');
CREATE TYPE notification_generation AS ENUM ('SYSTEM', 'MANUAL');
CREATE TYPE delivery_event_type AS ENUM (
  'ACCEPTED', 'DELIVERED', 'SOFT_BOUNCE', 'HARD_BOUNCE', 'OPENED',
  'CLICKED', 'SPAM', 'BLOCKED', 'ERROR', 'OTHER');
CREATE TYPE delivery_provider AS ENUM ('BREVO', 'STUB');

ALTER TYPE outbox_event_type ADD VALUE IF NOT EXISTS 'MANUAL_NOTIFICATION';

ALTER TABLE notifications
  ADD COLUMN dealership_id uuid REFERENCES dealerships (id),
  ADD COLUMN channel notification_channel,
  ADD COLUMN generation notification_generation,
  ADD COLUMN subject varchar(255),
  ADD COLUMN body text;

UPDATE notifications n
SET dealership_id = a.dealership_id,
    channel = 'EMAIL',
    generation = 'SYSTEM'
FROM appointments a
WHERE a.id = n.appointment_id;

ALTER TABLE notifications
  ALTER COLUMN dealership_id SET NOT NULL,
  ALTER COLUMN channel SET NOT NULL,
  ALTER COLUMN generation SET NOT NULL,
  ALTER COLUMN reminder_id DROP NOT NULL,
  ALTER COLUMN offset_minutes DROP NOT NULL;

ALTER TABLE notifications
  ADD CONSTRAINT notifications_generation_shape CHECK (
    (generation = 'SYSTEM'
      AND reminder_id IS NOT NULL
      AND offset_minutes IS NOT NULL
      AND subject IS NULL
      AND body IS NULL)
    OR
    (generation = 'MANUAL'
      AND reminder_id IS NULL
      AND offset_minutes IS NULL
      AND subject IS NOT NULL
      AND body IS NOT NULL)
  );

CREATE TABLE notification_delivery_events (
  id uuid PRIMARY KEY,
  notification_id uuid NOT NULL REFERENCES notifications (id),
  event_type delivery_event_type NOT NULL,
  provider delivery_provider NOT NULL,
  provider_event_id varchar(255) NOT NULL,
  occurred_at timestamptz NOT NULL,
  raw_type varchar(64),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT notification_delivery_events_unique UNIQUE (notification_id, provider, provider_event_id)
);

CREATE INDEX notifications_dealership_created_at
  ON notifications (dealership_id, created_at);

CREATE INDEX appointments_dealership_scheduled_at
  ON appointments (dealership_id, scheduled_at);

CREATE INDEX notification_delivery_events_notification_id
  ON notification_delivery_events (notification_id);

CREATE INDEX notification_delivery_events_type_occurred
  ON notification_delivery_events (event_type, occurred_at);
