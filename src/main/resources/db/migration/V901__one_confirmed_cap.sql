ALTER TABLE appointments
  ADD COLUMN one_confirmed boolean NOT NULL DEFAULT true;

DROP INDEX appointments_one_confirmed_per_vehicle;

CREATE UNIQUE INDEX appointments_one_confirmed_per_vehicle
  ON appointments (vehicle_id)
  WHERE status = 'CONFIRMED' AND one_confirmed;
