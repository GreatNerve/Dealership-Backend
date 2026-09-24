-- Brevo ts_epoch is milliseconds; ingest stored it as epoch seconds (year ~58699).
UPDATE notification_delivery_events
SET occurred_at = to_timestamp(EXTRACT(EPOCH FROM occurred_at) / 1000.0)
WHERE occurred_at > TIMESTAMPTZ '2100-01-01+00';
