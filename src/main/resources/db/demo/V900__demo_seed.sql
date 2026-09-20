INSERT INTO users (id, email, password_hash, role, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000002', 'staff@demo.local',
   '$2b$10$fIqnKa8LTm.xI5aVw1Du8uUhlMmrPlae6PgTHC9eoP4iF9/sMOYSq',
   'DEALERSHIP_STAFF', now(), now()),
  ('00000000-0000-4000-8000-000000000003', 'customer@demo.local',
   '$2b$10$fIqnKa8LTm.xI5aVw1Du8uUhlMmrPlae6PgTHC9eoP4iF9/sMOYSq',
   'CUSTOMER', now(), now());

INSERT INTO dealerships (id, name, timezone, address, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000001', 'Great Nerve Service', 'Asia/Kolkata',
   '1 MG Road, Bengaluru', now(), now());

INSERT INTO dealership_staff (id, user_id, dealership_id, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000005',
   '00000000-0000-4000-8000-000000000002',
   '00000000-0000-4000-8000-000000000001', now(), now());

INSERT INTO customers (id, user_id, contact, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000004',
   '00000000-0000-4000-8000-000000000003',
   'customer@demo.local', now(), now());

INSERT INTO vehicles (id, customer_id, registration_number, make, model, year, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000006',
   '00000000-0000-4000-8000-000000000004',
   'KA01AB1234', 'Honda', 'Civic', 2022, now(), now()),
  ('00000000-0000-4000-8000-000000000007',
   '00000000-0000-4000-8000-000000000004',
   'KA01CD5678', 'Honda', 'City', 2023, now(), now());
