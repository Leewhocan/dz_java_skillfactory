#!/usr/bin/env bash
#
# End-to-end scenario through the API gateway (http://localhost:8080).
#
#   1. register a USER and get its token
#   2. log in as the bootstrap ADMIN, register a second ADMIN and get its token
#   3. create a hotel + a room as that ADMIN
#   4. book the room with autoSelect=true as the USER
#   5. verify times_booked went from 0 to 1
#   6. try to book the same room for the same (and for an overlapping) period -> 409
#   7. book a disjoint period -> 201, cancel the first booking -> release, re-book -> 201
#   8. security matrix: anonymous 401, USER writes 403, internal endpoints not exposed
#   9. Swagger/OpenAPI descriptors are reachable
#
# Prerequisites: scripts/start-all.sh is running (Eureka, hotel, booking, gateway).
# Usage:  bash scripts/e2e-test.sh
#
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
HOTEL_DIRECT="${HOTEL_DIRECT:-http://localhost:8081}"
BOOKING_DIRECT="${BOOKING_DIRECT:-http://localhost:8082}"
INTERNAL_HEADER="${INTERNAL_API_HEADER:-X-Internal-Token}"
INTERNAL_TOKEN="${INTERNAL_API_TOKEN:-change-me-internal-service-token}"
BOOTSTRAP_ADMIN_USER="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
BOOTSTRAP_ADMIN_PASS="${BOOTSTRAP_ADMIN_PASSWORD:-admin123}"

STAMP="$(date +%s)"
USER_NAME="e2e_user_$STAMP"
USER_PASS="User123!"
ADMIN_NAME="e2e_admin_$STAMP"
ADMIN_PASS="Admin123!"

# booking periods: far enough in the future to pass validation
START="$(date -u -d '+30 days' +%Y-%m-%d)"
END="$(date -u -d '+33 days' +%Y-%m-%d)"
OVERLAP_START="$(date -u -d '+31 days' +%Y-%m-%d)"
OVERLAP_END="$(date -u -d '+34 days' +%Y-%m-%d)"
OTHER_START="$(date -u -d '+40 days' +%Y-%m-%d)"
OTHER_END="$(date -u -d '+42 days' +%Y-%m-%d)"
INTERNAL_START="$(date -u -d '+90 days' +%Y-%m-%d)"
INTERNAL_END="$(date -u -d '+92 days' +%Y-%m-%d)"

PASSED=0
FAILED=0
# api() runs inside a command substitution (subshell), so the status cannot be returned through a
# variable - it is handed over through a file instead.
STATUS_FILE="$(mktemp)"
trap 'rm -f "$STATUS_FILE"' EXIT

step() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok() { PASSED=$((PASSED + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no() { FAILED=$((FAILED + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }

# check <description> <expected> <actual>
check() {
  if [ "$2" = "$3" ]; then ok "$1 -> $3"; else no "$1: expected [$2], got [$3]"; fi
}

# api <METHOD> <url> [token] [json-body] -> prints body, stores the status for status()
api() {
  local method=$1 url=$2 token=${3:-} body=${4:-}
  local -a args=(-sS -m 20 -X "$method" "$url" -w '\n%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out
  out=$(curl "${args[@]}")
  printf '%s' "$out" | tail -1 >"$STATUS_FILE"
  printf '%s' "$out" | sed '$d'
}

# status -> HTTP status of the most recent api() call (read from STATUS_FILE)
status() { cat "$STATUS_FILE" 2>/dev/null; }

json_str() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
json_num() { printf '%s' "$1" | grep -o "\"$2\":[0-9]\+" | head -1 | cut -d: -f2; }

step "0. Gateway availability"
api GET "$GATEWAY/actuator/health" >/dev/null
check "GET /actuator/health (gateway)" "200" "$(status)"

step "1. Register USER ($USER_NAME) and obtain a token"
USER_AUTH=$(api POST "$GATEWAY/api/user/register" "" "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}")
check "POST /api/user/register" "201" "$(status)"
USER_TOKEN=$(json_str "$USER_AUTH" token)
check "role of the registered user" "USER" "$(json_str "$USER_AUTH" role)"
if [ -n "$USER_TOKEN" ]; then ok "user token received (${#USER_TOKEN} chars)"; else no "user token is empty"; fi

step "2. Register ADMIN ($ADMIN_NAME) and obtain a token"
BOOT_AUTH=$(api POST "$GATEWAY/api/user/auth" "" "{\"username\":\"$BOOTSTRAP_ADMIN_USER\",\"password\":\"$BOOTSTRAP_ADMIN_PASS\"}")
check "POST /api/user/auth (bootstrap admin)" "200" "$(status)"
BOOT_TOKEN=$(json_str "$BOOT_AUTH" token)
if [ -n "$BOOT_TOKEN" ]; then ok "bootstrap admin token received"; else no "bootstrap admin token is empty"; fi
api POST "$GATEWAY/api/user" "$BOOT_TOKEN" "{\"username\":\"$ADMIN_NAME\",\"password\":\"$ADMIN_PASS\",\"role\":\"ADMIN\"}" >/dev/null
check "POST /api/user (create ADMIN)" "201" "$(status)"
ADMIN_AUTH=$(api POST "$GATEWAY/api/user/auth" "" "{\"username\":\"$ADMIN_NAME\",\"password\":\"$ADMIN_PASS\"}")
check "POST /api/user/auth (new admin)" "200" "$(status)"
ADMIN_TOKEN=$(json_str "$ADMIN_AUTH" token)
check "role of the new admin" "ADMIN" "$(json_str "$ADMIN_AUTH" role)"

step "3. Create hotel and room (ADMIN token)"
HOTEL_BODY=$(api POST "$GATEWAY/api/hotels" "$ADMIN_TOKEN" "{\"name\":\"E2E Grand Hotel $STAMP\",\"address\":\"Tverskaya 1, Moscow\"}")
check "POST /api/hotels" "201" "$(status)"
HOTEL_ID=$(json_num "$HOTEL_BODY" id)
echo "     hotel: $HOTEL_BODY"
ROOM_BODY=$(api POST "$GATEWAY/api/rooms" "$ADMIN_TOKEN" "{\"hotelId\":$HOTEL_ID,\"number\":\"101\",\"available\":true}")
check "POST /api/rooms" "201" "$(status)"
ROOM_ID=$(json_num "$ROOM_BODY" id)
echo "     room:  $ROOM_BODY"

step "4. Booking with autoSelect=true (USER token)"
ROOMS_BEFORE=$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")
check "GET /api/rooms/recommend (USER)" "200" "$(status)"
check "times_booked before any booking" "0" "$(json_num "$ROOMS_BEFORE" timesBooked)"
echo "     recommend: $ROOMS_BEFORE"
BOOKING1=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"autoSelect\":true,\"startDate\":\"$START\",\"endDate\":\"$END\"}")
check "POST /api/booking (autoSelect=true, $START..$END)" "201" "$(status)"
BOOKING1_ID=$(json_num "$BOOKING1" id)
check "booking status" "CONFIRMED" "$(json_str "$BOOKING1" status)"
check "autoSelect picked the created room" "$ROOM_ID" "$(json_num "$BOOKING1" roomId)"
echo "     booking: $BOOKING1"

step "5. times_booked increased"
ROOMS_AFTER=$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")
check "times_booked after the booking" "1" "$(json_num "$ROOMS_AFTER" timesBooked)"
echo "     recommend: $ROOMS_AFTER"

step "6. The same room for the same / overlapping dates must be rejected (409)"
CONFLICT1=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"roomId\":$ROOM_ID,\"startDate\":\"$START\",\"endDate\":\"$END\"}")
check "same period $START..$END" "409" "$(status)"
echo "     response: $CONFLICT1"
CONFLICT2=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"roomId\":$ROOM_ID,\"startDate\":\"$OVERLAP_START\",\"endDate\":\"$OVERLAP_END\"}")
check "overlapping period $OVERLAP_START..$OVERLAP_END" "409" "$(status)"
echo "     response: $CONFLICT2"
CONFLICT3=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"autoSelect\":true,\"startDate\":\"$OVERLAP_START\",\"endDate\":\"$OVERLAP_END\"}")
check "autoSelect over a fully blocked period" "409" "$(status)"
echo "     response: $CONFLICT3"
check "times_booked unchanged after the rejections" "1" \
  "$(json_num "$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")" timesBooked)"

step "7. A disjoint period is bookable"
BOOKING2=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"roomId\":$ROOM_ID,\"startDate\":\"$OTHER_START\",\"endDate\":\"$OTHER_END\"}")
check "disjoint period $OTHER_START..$OTHER_END" "201" "$(status)"
check "second booking status" "CONFIRMED" "$(json_str "$BOOKING2" status)"
check "times_booked after the 2nd booking" "2" \
  "$(json_num "$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")" timesBooked)"

step "8. Cancel the first booking (release) and re-book the same period"
api DELETE "$GATEWAY/api/booking/$BOOKING1_ID" "$USER_TOKEN" >/dev/null
check "DELETE /api/booking/$BOOKING1_ID" "204" "$(status)"
check "times_booked after the release" "1" \
  "$(json_num "$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")" timesBooked)"
BOOKING3=$(api POST "$GATEWAY/api/booking" "$USER_TOKEN" "{\"roomId\":$ROOM_ID,\"startDate\":\"$START\",\"endDate\":\"$END\"}")
check "re-book the released period" "201" "$(status)"
check "times_booked after the re-booking" "2" \
  "$(json_num "$(api GET "$GATEWAY/api/rooms/recommend" "$USER_TOKEN")" timesBooked)"

step "9. Security matrix (gateway)"
api GET "$GATEWAY/api/hotels" "" >/dev/null
check "GET /api/hotels anonymous" "401" "$(status)"
api GET "$GATEWAY/api/hotels" "$USER_TOKEN" >/dev/null
check "GET /api/hotels as USER" "200" "$(status)"
api GET "$GATEWAY/api/rooms" "" >/dev/null
check "GET /api/rooms anonymous" "401" "$(status)"
api POST "$GATEWAY/api/hotels" "$USER_TOKEN" "{\"name\":\"Forbidden\",\"address\":\"Nowhere\"}" >/dev/null
check "POST /api/hotels as USER" "403" "$(status)"
api POST "$GATEWAY/api/rooms" "$USER_TOKEN" "{\"hotelId\":$HOTEL_ID,\"number\":\"999\"}" >/dev/null
check "POST /api/rooms as USER" "403" "$(status)"
api POST "$GATEWAY/api/rooms/$ROOM_ID/release" "$ADMIN_TOKEN" "{\"startDate\":\"$START\",\"endDate\":\"$END\"}" >/dev/null
check "internal /release is not exposed through the gateway" "403" "$(status)"

step "10. Internal saga contract (direct call to hotel-service, X-Internal-Token)"
api POST "$HOTEL_DIRECT/api/rooms/$ROOM_ID/confirm-availability" "$ADMIN_TOKEN" "{\"startDate\":\"$INTERNAL_START\",\"endDate\":\"$INTERNAL_END\"}" >/dev/null
check "confirm-availability without the secret" "403" "$(status)"
INT_BODY="{\"startDate\":\"$INTERNAL_START\",\"endDate\":\"$INTERNAL_END\"}"
INT_CONFIRM=$(curl -sS -m 20 -X POST "$HOTEL_DIRECT/api/rooms/$ROOM_ID/confirm-availability" \
  -H 'Content-Type: application/json' -H "$INTERNAL_HEADER: $INTERNAL_TOKEN" \
  -d "$INT_BODY" -w '\n%{http_code}')
check "confirm-availability with the secret" "200" "$(printf '%s' "$INT_CONFIRM" | tail -1)"
echo "     response: $(printf '%s' "$INT_CONFIRM" | sed '$d')"
INT_RELEASE=$(curl -sS -m 20 -o /dev/null -X POST "$HOTEL_DIRECT/api/rooms/$ROOM_ID/release" \
  -H 'Content-Type: application/json' -H "$INTERNAL_HEADER: $INTERNAL_TOKEN" \
  -d "$INT_BODY" -w '%{http_code}')
check "release with the secret" "204" "$INT_RELEASE"

step "11. Swagger / OpenAPI"
check "hotel-service /v3/api-docs" "200" "$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$HOTEL_DIRECT/v3/api-docs")"
check "hotel-service OpenAPI title" "Hotel service API" \
  "$(json_str "$(curl -sS -m 20 "$HOTEL_DIRECT/v3/api-docs")" title)"
check "hotel-service /swagger-ui/index.html" "200" \
  "$(curl -s -o /dev/null -m 20 -L -w '%{http_code}' "$HOTEL_DIRECT/swagger-ui/index.html")"
check "booking-service /v3/api-docs" "200" "$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$BOOKING_DIRECT/v3/api-docs")"
check "booking-service OpenAPI title" "Booking service API" \
  "$(json_str "$(curl -sS -m 20 "$BOOKING_DIRECT/v3/api-docs")" title)"
check "booking-service /swagger-ui/index.html" "200" \
  "$(curl -s -o /dev/null -m 20 -L -w '%{http_code}' "$BOOKING_DIRECT/swagger-ui/index.html")"

printf '\n\033[1mE2E SUMMARY: %d passed, %d failed\033[0m\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ] || exit 1


