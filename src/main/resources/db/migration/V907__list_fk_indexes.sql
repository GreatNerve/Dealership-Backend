CREATE INDEX IF NOT EXISTS appointments_customer_id ON appointments (customer_id);
CREATE INDEX IF NOT EXISTS appointments_dealership_id ON appointments (dealership_id);
CREATE INDEX IF NOT EXISTS vehicles_customer_id ON vehicles (customer_id);
CREATE INDEX IF NOT EXISTS notifications_appointment_id ON notifications (appointment_id);
CREATE INDEX IF NOT EXISTS notifications_reminder_id ON notifications (reminder_id);
