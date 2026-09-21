ALTER TABLE idempotency_keys ADD COLUMN user_id uuid REFERENCES users (id);

UPDATE idempotency_keys k
SET user_id = a.created_by_user_id
FROM appointments a
WHERE k.resource_id = a.id
  AND k.user_id IS NULL;

DELETE FROM idempotency_keys WHERE user_id IS NULL;

ALTER TABLE idempotency_keys ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE idempotency_keys DROP CONSTRAINT idempotency_keys_key_key;

CREATE UNIQUE INDEX idempotency_keys_user_key ON idempotency_keys (user_id, key);
