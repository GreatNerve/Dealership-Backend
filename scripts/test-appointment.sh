#!/usr/bin/env bash
# Create Vehicle + Appointment and fire a Reminder without waiting 24 real hours.
#   24h → visit in 20 hours   (24h Reminder already due)
#   2h  → visit in 100 minutes (2h Reminder already due)
# Pass the API URL. Offset is optional (default both).
#   bash scripts/test-appointment.sh http://localhost:8080
#   bash scripts/test-appointment.sh https://dealership.greatnerve.com
#   bash scripts/test-appointment.sh http://localhost:8080 24h
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="${LOG:-$ROOT/logs/notifications.log}"
PASSWORD="password1"
BASE=""
WHICH="both"

usage() {
  echo "usage: $0 <url> [24h|2h|both]" >&2
  echo "  $0 http://localhost:8080" >&2
  echo "  $0 https://dealership.greatnerve.com" >&2
  echo "  $0 http://localhost:8080 24h" >&2
  exit 1
}

for arg in "$@"; do
  case "$arg" in
    24h | 2h | both) WHICH="$arg" ;;
    http://* | https://*) BASE="${arg%/}" ;;
    *) usage ;;
  esac
done
[[ -n "$BASE" ]] || usage

if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
elif command -v py >/dev/null 2>&1; then
  PY=py
else
  echo "python3, python, or py is required (JSON + ISO scheduledAt)" >&2
  exit 1
fi

json_get() {
  "$PY" -c "
import json, sys
raw, path = sys.argv[1], sys.argv[2]
try:
    d = json.loads(raw)
except json.JSONDecodeError:
    sys.stderr.write(raw + '\n')
    raise SystemExit('response was not JSON')
cur = d
try:
    for p in path.split('.'):
        cur = cur[int(p)] if isinstance(cur, list) else cur[p]
except (KeyError, IndexError, TypeError, ValueError):
    sys.stderr.write(raw + '\n')
    raise SystemExit('missing ' + path)
if cur is None:
    err = d.get('error') if isinstance(d, dict) else None
    msg = d.get('message') if isinstance(d, dict) else None
    sys.stderr.write(raw + '\n')
    raise SystemExit(msg or err or ('missing ' + path))
print(cur)
" "$1" "$2"
}

iso_ahead() {
  "$PY" -c "
from datetime import datetime, timedelta, timezone
import sys
hours, minutes = int(sys.argv[1]), int(sys.argv[2])
off = timezone(timedelta(hours=5, minutes=30))
when = datetime.now(timezone.utc).astimezone(off) + timedelta(hours=hours, minutes=minutes)
stamp = when.strftime('%Y-%m-%dT%H:%M:%S')
z = when.strftime('%z')
print(stamp + z[:3] + ':' + z[3:])
" "$1" "$2"
}

api() {
  local method="$1"
  local path="$2"
  shift 2
  curl -sS -X "$method" "$BASE$path" "$@"
}

TOKEN=""
DEALERSHIP_ID=""

login_demo() {
  local body shops
  body="$(api POST /api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"customer@greatnerve.com","password":"password1"}')"
  TOKEN="$(json_get "$body" data.access_token)"
  shops="$(api GET '/api/v1/dealerships?size=1' -H "Authorization: Bearer $TOKEN")"
  DEALERSHIP_ID="$(json_get "$shops" data.items.0.id)"
}

provision_fresh() {
  local stamp rand staff_email cust_email staff_login shop cust_login staff_token
  stamp="$(date +%s)"
  rand="$RANDOM"
  staff_email="staff-appt-${stamp}-${rand}@ex.com"
  cust_email="cust-appt-${stamp}-${rand}@ex.com"

  echo "register staff $staff_email"
  json_get "$(api POST /api/v1/auth/register \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$staff_email\",\"password\":\"$PASSWORD\",\"role\":\"DEALERSHIP_STAFF\"}")" data.id >/dev/null

  staff_login="$(api POST /api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$staff_email\",\"password\":\"$PASSWORD\"}")"
  staff_token="$(json_get "$staff_login" data.access_token)"

  shop="$(api POST /api/v1/dealerships \
    -H "Authorization: Bearer $staff_token" \
    -H 'Content-Type: application/json' \
    -d '{"name":"Appointment Test Shop","timezone":"Asia/Kolkata","address":"1 Road"}')"
  DEALERSHIP_ID="$(json_get "$shop" data.id)"

  echo "register customer $cust_email"
  json_get "$(api POST /api/v1/auth/register \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$cust_email\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")" data.id >/dev/null

  cust_login="$(api POST /api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$cust_email\",\"password\":\"$PASSWORD\"}")"
  TOKEN="$(json_get "$cust_login" data.access_token)"
}

book() {
  local label="$1"
  local hours="$2"
  local minutes="$3"
  local plate="$4"
  local when vehicle vehicle_id key created appt
  when="$(iso_ahead "$hours" "$minutes")"
  echo
  echo "== $label =="
  echo "scheduledAt=$when  notify=false"

  vehicle="$(api POST /api/v1/vehicles \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"registrationNumber\":\"$plate\",\"make\":\"Honda\",\"model\":\"Civic\",\"year\":2022}")"
  vehicle_id="$(json_get "$vehicle" data.id)"
  echo "vehicleId=$vehicle_id"

  key="$("$PY" -c 'import uuid; print(uuid.uuid4())')"
  created="$(api POST /api/v1/appointments \
    -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $key" \
    -H 'Content-Type: application/json' \
    -d "{\"vehicleId\":\"$vehicle_id\",\"dealershipId\":\"$DEALERSHIP_ID\",\"scheduledAt\":\"$when\",\"notify\":false}")"
  appt="$(json_get "$created" data.id)"
  echo "appointmentId=$appt"

  api GET "/api/v1/appointments/$appt" -H "Authorization: Bearer $TOKEN"
  echo

  echo "wait ~4s for poller"
  sleep 4
  if [[ "$BASE" == *localhost* || "$BASE" == *127.0.0.1* ]]; then
    echo "→ $LOG"
    if [[ -f "$LOG" ]]; then
      grep -F "appointment_id=$appt" "$LOG" || echo "(no line yet for $appt — sleep a few more seconds and grep $LOG)"
    else
      echo "missing $LOG (need notify:false and APP_NOTIFICATIONS_LOG_DIR=logs)"
    fi
  else
    echo "file log is on the server (notify:false)"
  fi
}

echo "======== $BASE ========"
echo "GET $BASE/actuator/health"
api GET /actuator/health
echo

if login_demo 2>/dev/null; then
  echo "logged in as customer@greatnerve.com"
else
  echo "demo login skipped; registering a new Customer"
  provision_fresh
fi
echo "dealershipId=$DEALERSHIP_ID"

PLATE="KA$(date +%H%M%S)$(printf '%02d' $((RANDOM % 100)))"

case "$WHICH" in
  24h)
    book "24h Reminder (visit ~20h from now)" 20 0 "${PLATE}A"
    ;;
  2h)
    book "2h Reminder (visit ~100m from now)" 1 40 "${PLATE}B"
    ;;
  both)
    book "24h Reminder (visit ~20h from now)" 20 0 "${PLATE}A"
    book "2h Reminder (visit ~100m from now)" 1 40 "${PLATE}B"
    ;;
esac

echo
echo "done. 24h log offset=1d  |  2h log offset=2h"
