# Manual Appointment test (curl)

JUnit under `src/test/java` is the suite. This file is the curl flow: login, Vehicle, Appointment, then the 24h and 2h Reminders.

Pass the API URL (local or prod). Offset is optional (`24h`, `2h`, or both).

```bash
make run
bash scripts/test-appointment.sh http://localhost:8080
bash scripts/test-appointment.sh https://dealership.greatnerve.com
bash scripts/test-appointment.sh http://localhost:8080 24h
bash scripts/test-appointment.sh https://dealership.greatnerve.com 2h
```

`notify: false` appends `logs/notifications.log` (ids + wall time, no contact). The poller is already running; do **not** wait 24 real hours.

Two bookings, because one visit time cannot fire both offsets immediately:

| Reminder | `scheduledAt` | Why |
| --- | --- | --- |
| 24h | now **+ 20 hours** | 24h Reminder is already due |
| 2h | now **+ 100 minutes** | 2h Reminder is already due (90 minutes is too late) |

24h log line uses `offset=1d`. 2h uses `offset=2h`.

Login, register, and every other route are **15 requests / 60 seconds** per endpoint. A reviewer can click Swagger without a 15-minute lockout. Reuse `TOKEN` (7 days) so you do not spend login tokens.

Needs `curl` and `python` (JSON + ISO times). Envelope is `{ success, data, ... }`; the token is `data.access_token`.

---

## 0. App up

```bash
export BASE=http://localhost:8080
curl -sS "$BASE/actuator/health"
```

## 1. Token (demo Customer)

```bash
LOGIN=$(curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@greatnerve.com","password":"password1"}')
echo "$LOGIN"
TOKEN=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['access_token'])" "$LOGIN")
```

If demo seed is missing, register once (counts against the **register** bucket, not login):

```bash
curl -sS -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"cust-appt@ex.com","password":"password1","role":"CUSTOMER"}'
```

Staff + shop only if `GET /dealerships` is empty. Demo shop id is `00000000-0000-4000-8000-000000000001`.

## 2. Dealership id

```bash
SHOPS=$(curl -sS "$BASE/api/v1/dealerships?size=1" -H "Authorization: Bearer $TOKEN")
DEALERSHIP_ID=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['items'][0]['id'])" "$SHOPS")
echo "$DEALERSHIP_ID"
```

## 3. Vehicle

Use a **new** Vehicle Number each run (uppercase, unique). Cap on → 409 if that Vehicle already has a Confirmed Appointment.

```bash
PLATE=KA$(date +%H%M%S)99
VEHICLE=$(curl -sS -X POST "$BASE/api/v1/vehicles" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"registrationNumber\":\"${PLATE}A\",\"make\":\"Honda\",\"model\":\"Civic\",\"year\":2022}")
VEHICLE_ID=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['id'])" "$VEHICLE")
echo "$VEHICLE_ID"
```

## 4a. Appointment that fires the **24h** Reminder

Visit in ~20 hours. `Idempotency-Key` is required. `notify: false` writes the log file.

```bash
WHEN_24H=$(python -c "
from datetime import datetime, timedelta, timezone
off = timezone(timedelta(hours=5, minutes=30))
when = datetime.now(timezone.utc).astimezone(off) + timedelta(hours=20)
stamp = when.strftime('%Y-%m-%dT%H:%M:%S')
z = when.strftime('%z')
print(stamp + z[:3] + ':' + z[3:])
")
KEY_24H=$(python -c "import uuid; print(uuid.uuid4())")

CREATED_24H=$(curl -sS -X POST "$BASE/api/v1/appointments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY_24H" \
  -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":\"$VEHICLE_ID\",\"dealershipId\":\"$DEALERSHIP_ID\",\"scheduledAt\":\"$WHEN_24H\",\"notify\":false}")
echo "$CREATED_24H"
APPT_24H=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['id'])" "$CREATED_24H")

curl -sS "$BASE/api/v1/appointments/$APPT_24H" -H "Authorization: Bearer $TOKEN"
sleep 4
grep "appointment_id=$APPT_24H" logs/notifications.log
```

Expect `notify=false` and `offset=1d`.

## 4b. Appointment that fires the **2h** Reminder

New Vehicle (or the cap must be off). Visit in ~100 minutes — not 90.

```bash
VEHICLE2=$(curl -sS -X POST "$BASE/api/v1/vehicles" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"registrationNumber\":\"${PLATE}B\",\"make\":\"Honda\",\"model\":\"Civic\",\"year\":2022}")
VEHICLE2_ID=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['id'])" "$VEHICLE2")

WHEN_2H=$(python -c "
from datetime import datetime, timedelta, timezone
off = timezone(timedelta(hours=5, minutes=30))
when = datetime.now(timezone.utc).astimezone(off) + timedelta(hours=1, minutes=40)
stamp = when.strftime('%Y-%m-%dT%H:%M:%S')
z = when.strftime('%z')
print(stamp + z[:3] + ':' + z[3:])
")
KEY_2H=$(python -c "import uuid; print(uuid.uuid4())")

CREATED_2H=$(curl -sS -X POST "$BASE/api/v1/appointments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY_2H" \
  -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":\"$VEHICLE2_ID\",\"dealershipId\":\"$DEALERSHIP_ID\",\"scheduledAt\":\"$WHEN_2H\",\"notify\":false}")
echo "$CREATED_2H"
APPT_2H=$(python -c "import json,sys; print(json.loads(sys.argv[1])['data']['id'])" "$CREATED_2H")

curl -sS "$BASE/api/v1/appointments/$APPT_2H" -H "Authorization: Bearer $TOKEN"
sleep 4
grep "appointment_id=$APPT_2H" logs/notifications.log
```

Expect `offset=2h`.

## 429

Every endpoint is **15 / 60 seconds** (login, register, Vehicle, Appointment — each its own bucket). `Retry-After` is at most ~60s. Reuse `TOKEN` (7 days).
