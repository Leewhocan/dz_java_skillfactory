#!/usr/bin/env bash
#
# Starts the whole stack in the background in the required order:
#   Eureka -> hotel-service -> booking-service -> api-gateway
#
# Logs and PIDs are written to ./logs/*.out.log and ./logs/*.pid.
# Stop everything with scripts/stop-all.sh.
#
# Usage:  bash scripts/start-all.sh
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$ROOT/logs}"
mkdir -p "$LOG_DIR"

EUREKA_JAR="$ROOT/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar"
HOTEL_JAR="$ROOT/hotel-service/target/hotel-service-1.0.0-SNAPSHOT.jar"
BOOKING_JAR="$ROOT/booking-service/target/booking-service-1.0.0-SNAPSHOT.jar"
GATEWAY_JAR="$ROOT/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"

for jar in "$EUREKA_JAR" "$HOTEL_JAR" "$BOOKING_JAR" "$GATEWAY_JAR"; do
  if [ ! -f "$jar" ]; then
    echo "Missing $jar - run 'mvn -DskipTests package' first" >&2
    exit 1
  fi
done

# Windows-friendly: make the jar path understandable for java.exe
win_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

launch() {
  local name=$1 jar=$2
  local out="$LOG_DIR/$name.out.log" err="$LOG_DIR/$name.err.log"
  local pidfile="$LOG_DIR/$name.pid"
  : >"$out"
  : >"$err"
  rm -f "$pidfile"

  local pid=""
  if command -v powershell >/dev/null 2>&1; then
    # The PID is written to a file by PowerShell itself and stdout is discarded: capturing it via
    # $(...) would never return, because the started java process inherits the pipe and keeps it open.
    powershell -NoProfile -Command \
      "\$p = Start-Process -FilePath 'java' -ArgumentList @('-jar','$(win_path "$jar")') \
       -RedirectStandardOutput '$(win_path "$out")' -RedirectStandardError '$(win_path "$err")' \
       -WindowStyle Hidden -PassThru; \
       \$p.Id | Out-File -FilePath '$(win_path "$pidfile")' -Encoding ascii" >/dev/null 2>&1
  else
    nohup java -jar "$jar" >"$out" 2>"$err" &
    pid=$!
    echo "$pid" >"$pidfile"
  fi

  pid=$(tr -d '\r\n' <"$pidfile" 2>/dev/null)
  echo "  started $name (pid ${pid:-unknown}) -> $out"
}

wait_for() {
  local name=$1 url=$2 attempts=${3:-90}
  printf '  waiting for %s' "$name"
  for _ in $(seq 1 "$attempts"); do
    code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' "$url" 2>/dev/null)
    if [ "$code" != "000" ] && [ -n "$code" ]; then
      echo " - up (HTTP $code)"
      return 0
    fi
    printf '.'
    sleep 2
  done
  echo " - TIMEOUT"
  return 1
}

fail=0

echo "[1/4] eureka-server (8761)"
launch eureka-server "$EUREKA_JAR"
wait_for eureka-server "http://localhost:8761/eureka/apps" 90 || fail=1

echo "[2/4] hotel-service (8081)"
launch hotel-service "$HOTEL_JAR"
wait_for hotel-service "http://localhost:8081/actuator/health" 90 || fail=1

echo "[3/4] booking-service (8082)"
launch booking-service "$BOOKING_JAR"
wait_for booking-service "http://localhost:8082/actuator/health" 90 || fail=1

echo "[4/4] api-gateway (8080)"
launch api-gateway "$GATEWAY_JAR"
wait_for api-gateway "http://localhost:8080/actuator/health" 90 || fail=1

if [ "$fail" -ne 0 ]; then
  echo "One or more services did not start - check $LOG_DIR" >&2
  exit 1
fi

echo
echo "All services are up:"
echo "  Eureka         http://localhost:8761"
echo "  hotel-service  http://localhost:8081/swagger-ui.html"
echo "  booking-service http://localhost:8082/swagger-ui.html"
echo "  gateway        http://localhost:8080"
