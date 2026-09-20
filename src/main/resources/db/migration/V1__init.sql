CREATE TYPE user_role AS ENUM ('CUSTOMER', 'DEALERSHIP_STAFF');
CREATE TYPE appointment_status AS ENUM ('CONFIRMED', 'CANCELLED', 'NO_SHOW_EXPIRED');
CREATE TYPE idempotency_status AS ENUM ('STARTED', 'COMPLETED');
CREATE TYPE reminder_status AS ENUM (
  'PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'SENT', 'DEAD_LETTER', 'CANCELLED', 'EXPIRED');
CREATE TYPE notification_status AS ENUM (
  'PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'SENT', 'DEAD_LETTER', 'CANCELLED');
CREATE TYPE outbox_event_type AS ENUM ('REMINDER_DUE');
CREATE TYPE outbox_status AS ENUM ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'PUBLISHED');

CREATE TABLE users (
  id uuid PRIMARY KEY,
  email varchar(320) NOT NULL,
  password_hash varchar(255) NOT NULL,
  role user_role NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE dealerships (
  id uuid PRIMARY KEY,
  name varchar(255) NOT NULL,
  timezone varchar(64) NOT NULL,
  address varchar(512) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE dealership_staff (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL UNIQUE REFERENCES users (id),
  dealership_id uuid NOT NULL REFERENCES dealerships (id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE customers (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL UNIQUE REFERENCES users (id),
  contact varchar(320) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE vehicles (
  id uuid PRIMARY KEY,
  customer_id uuid NOT NULL REFERENCES customers (id),
  registration_number varchar(13) NOT NULL,
  make varchar(64) NOT NULL,
  model varchar(64) NOT NULL,
  year int NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT vehicles_registration_number_unique UNIQUE (registration_number)
);

CREATE TABLE appointments (
  id uuid PRIMARY KEY,
  customer_id uuid NOT NULL REFERENCES customers (id),
  vehicle_id uuid NOT NULL REFERENCES vehicles (id),
  dealership_id uuid NOT NULL REFERENCES dealerships (id),
  scheduled_at timestamptz NOT NULL,
  display_offset varchar(9) NOT NULL,
  status appointment_status NOT NULL,
  created_by_user_id uuid NOT NULL REFERENCES users (id),
  created_by_role user_role NOT NULL,
  schedule_version int NOT NULL DEFAULT 1,
  notify boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX appointments_one_confirmed_per_vehicle
  ON appointments (vehicle_id)
  WHERE status = 'CONFIRMED';

CREATE INDEX appointments_confirmed_scheduled_at
  ON appointments (scheduled_at)
  WHERE status = 'CONFIRMED';

CREATE TABLE idempotency_keys (
  id uuid PRIMARY KEY,
  key varchar(255) NOT NULL UNIQUE,
  fingerprint varchar(64) NOT NULL,
  resource_id uuid,
  status idempotency_status NOT NULL,
  response text,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE reminders (
  id uuid PRIMARY KEY,
  appointment_id uuid NOT NULL REFERENCES appointments (id),
  offset_minutes int NOT NULL CHECK (offset_minutes > 0),
  schedule_version int NOT NULL,
  scheduled_at timestamptz NOT NULL,
  status reminder_status NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  last_error varchar(1024),
  locked_by varchar(64),
  locked_at timestamptz,
  lease_expires_at timestamptz,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT reminders_unique_offset_version UNIQUE (appointment_id, offset_minutes, schedule_version)
);

CREATE INDEX reminders_due_scheduled_at
  ON reminders (scheduled_at)
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE TABLE notifications (
  id uuid PRIMARY KEY,
  reminder_id uuid NOT NULL REFERENCES reminders (id),
  appointment_id uuid NOT NULL REFERENCES appointments (id),
  offset_minutes int NOT NULL CHECK (offset_minutes > 0),
  idempotency_key varchar(255) NOT NULL UNIQUE,
  status notification_status NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  last_error varchar(1024),
  sent_at timestamptz,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE outbox_events (
  id uuid PRIMARY KEY,
  event_type outbox_event_type NOT NULL,
  aggregate_id uuid NOT NULL,
  payload jsonb NOT NULL,
  status outbox_status NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  last_error varchar(1024),
  locked_by varchar(64),
  locked_at timestamptz,
  lease_expires_at timestamptz,
  published_at timestamptz,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE INDEX outbox_events_pending
  ON outbox_events (created_at)
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED');
