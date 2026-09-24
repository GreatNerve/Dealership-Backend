INSERT INTO users (id, email, password_hash, role, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000002', 'staff@greatnerve.com',
   '$2b$10$WjdtgMqEGZqT/eSiEDc4ou9JLyVl2zQXN9PCwwcKDtx0kGes1T/76',
   'DEALERSHIP_STAFF', now(), now()),
  ('00000000-0000-4000-8000-000000000003', 'customer@greatnerve.com',
   '$2b$10$WjdtgMqEGZqT/eSiEDc4ou9JLyVl2zQXN9PCwwcKDtx0kGes1T/76',
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
   'customer@greatnerve.com', now(), now());

INSERT INTO vehicles (id, customer_id, registration_number, make, model, year, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000006',
   '00000000-0000-4000-8000-000000000004',
   'KA01AB1234', 'Honda', 'Civic', 2022, now(), now()),
  ('00000000-0000-4000-8000-000000000007',
   '00000000-0000-4000-8000-000000000004',
   'KA01CD5678', 'Honda', 'City', 2023, now(), now());
