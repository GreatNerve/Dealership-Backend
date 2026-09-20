# 0008 UUID primary keys

Public resource ids are UUID (Postgres `uuid`, Java `UUID`) so demo URLs are not enumerable. Business uniqueness stays on real columns: **Vehicle Number**, Idempotency Key, `(appointment_id, offset_minutes, schedule_version)`, Confirmed `vehicle_id`.

**Status:** accepted
