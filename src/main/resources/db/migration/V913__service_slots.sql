ALTER TABLE dealerships
  ADD COLUMN default_capacity int NOT NULL DEFAULT 10;

ALTER TABLE dealerships
  ADD CONSTRAINT dealerships_default_capacity_nonneg CHECK (default_capacity >= 0);

CREATE TABLE dealership_hours (
  id uuid PRIMARY KEY,
  dealership_id uuid NOT NULL REFERENCES dealerships (id),
  weekday int NOT NULL,
  closed boolean NOT NULL,
  open_time time,
  close_time time,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT dealership_hours_weekday CHECK (weekday BETWEEN 1 AND 7),
  CONSTRAINT dealership_hours_shop_weekday UNIQUE (dealership_id, weekday),
  CONSTRAINT dealership_hours_times CHECK (
    (closed AND open_time IS NULL AND close_time IS NULL)
    OR (NOT closed AND open_time IS NOT NULL AND close_time IS NOT NULL)
  )
);

CREATE TABLE dealership_capacity_overrides (
  id uuid PRIMARY KEY,
  dealership_id uuid NOT NULL REFERENCES dealerships (id),
  from_date date NOT NULL,
  to_date date NOT NULL,
  from_time time,
  to_time time,
  capacity int NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT dealership_overrides_dates CHECK (from_date <= to_date),
  CONSTRAINT dealership_overrides_capacity CHECK (capacity >= 0),
  CONSTRAINT dealership_overrides_times CHECK (
    (from_time IS NULL AND to_time IS NULL)
    OR (from_time IS NOT NULL AND to_time IS NOT NULL AND from_time < to_time)
  )
);

CREATE INDEX dealership_overrides_shop_dates
  ON dealership_capacity_overrides (dealership_id, from_date, to_date);

CREATE INDEX appointments_confirmed_shop_slot
  ON appointments (dealership_id, scheduled_at)
  WHERE status = 'CONFIRMED';

INSERT INTO dealership_hours (
  id, dealership_id, weekday, closed, open_time, close_time, created_at, updated_at)
SELECT
  gen_random_uuid(),
  d.id,
  w.weekday,
  w.weekday = 7,
  CASE WHEN w.weekday = 7 THEN NULL ELSE time '09:00' END,
  CASE WHEN w.weekday = 7 THEN NULL ELSE time '18:00' END,
  now(),
  now()
FROM dealerships d
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7)) AS w(weekday);
