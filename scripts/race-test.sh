#!/usr/bin/env bash
#
# Concurrency test for the date-overlap protection (the core of Stage 4).
#
# N clients try to book the SAME room for the SAME period at the same moment. Exactly one booking
# must win (201 CONFIRMED), every other request must be rejected with 409 - never a double booking.
#
#   - hotel-service serialises the check with a PESSIMISTIC_WRITE lock on the room row;
#   - the unique constraint (room_id, start_date, end_date) is the safety net.
#
# Prerequisites: scripts/start-all.sh is running.
# Usage:  bash scripts/race-test.sh [concurrent-clients]   (default 5)
#
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
CLIENTS="${1:-5}"
BOOTSTRAP_ADMIN_USER="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
BOOTSTRAP_ADMIN_PASS="${BOOTSTRAP_ADMIN_PASSWORD:-admin123}"

STAMP="$(date +%s)"
USER_NAME="race_user_$STAMP"
USER_PASS="User123!"
START="$(date -u -d '+120 days' +%Y-%m-%d)"
END="$(date -u -d '+123 days' +%Y-%m-%d)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

json_str() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
json_num() { printf '%s' "$1" | grep -o "\"$2\":[0-9]\+" | head -1 | cut -d: -f2; }

echo "1. Registering $USER_NAME and creating a dedicated hotel/room"
USER_AUTH=$(curl -sS -X POST "$GATEWAY/api/user/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}")
USER_TOKEN=$(json_str "$USER_AUTH" token)
[ -n "$USER_TOKEN" ] || { echo "FAIL: no user token"; exit 1; }

ADMIN_TOKEN=$(json_str "$(curl -sS -X POST "$GATEWAY/api/user/auth" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$BOOTSTRAP_ADMIN_USER\",\"password\":\"$BOOTSTRAP_ADMIN_PASS\"}")" token)
[ -n "$ADMIN_TOKEN" ] || { echo "FAIL: no admin token"; exit 1; }

HOTEL=$(curl -sS -X POST "$GATEWAY/api/hotels" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"name\":\"Race Hotel $STAMP\",\"address\":\"Race street 1\"}")
HOTEL_ID=$(json_num "$HOTEL" id)
ROOM=$(curl -sS -X POST "$GATEWAY/api/rooms" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"hotelId\":$HOTEL_ID,\"number\":\"R-$STAMP\"}")
ROOM_ID=$(json_num "$ROOM" id)
[ -n "$ROOM_ID" ] || { echo "FAIL: no room created"; exit 1; }
echo "   room=$ROOM_ID, period=$START..$END"

echo "2. Firing $CLIENTS concurrent bookings for the same room and period"
BODY="{\"roomId\":$ROOM_ID,\"startDate\":\"$START\",\"endDate\":\"$END\"}"
for i in $(seq 1 "$CLIENTS"); do
  (
    curl -sS -m 30 -o /dev/null -X POST "$GATEWAY/api/booking" \
      -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
      -d "$BODY" -w '%{http_code}' >"$TMP_DIR/status.$i"
  ) &
done
wait

CONFIRMED=0
CONFLICTS=0
OTHER=0
for i in $(seq 1 "$CLIENTS"); do
  code="$(cat "$TMP_DIR/status.$i" 2>/dev/null)"
  case "$code" in
    201) CONFIRMED=$((CONFIRMED + 1)) ;;
    409) CONFLICTS=$((CONFLICTS + 1)) ;;
    *)   OTHER=$((OTHER + 1)); echo "   unexpected status: ${code:-<none>}" ;;
  esac
done

echo "   results: $CONFIRMED x 201, $CONFLICTS x 409, $OTHER x other"

echo "3. Verifying that the room is counted exactly once"
TIMES=$(json_num "$(curl -sS "$GATEWAY/api/rooms/recommend" -H "Authorization: Bearer $USER_TOKEN" \
  | tr '}' '\n' | grep "\"id\":$ROOM_ID,")" timesBooked)
echo "   timesBooked=$TIMES"

FAILED=0
[ "$CONFIRMED" -eq 1 ] || { echo "FAIL: expected exactly one 201, got $CONFIRMED"; FAILED=1; }
[ "$CONFLICTS" -eq $((CLIENTS - 1)) ] || { echo "FAIL: expected $((CLIENTS - 1)) x 409, got $CONFLICTS"; FAILED=1; }
[ "$TIMES" = "1" ] || { echo "FAIL: expected timesBooked=1, got ${TIMES:-<empty>}"; FAILED=1; }

if [ "$FAILED" -eq 0 ]; then
  echo "RACE TEST PASSED: 1 winner, $CONFLICTS rejected with 409, no double booking"
else
  echo "RACE TEST FAILED"
  exit 1
fi
