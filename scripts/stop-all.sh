#!/usr/bin/env bash
#
# Stops everything started by scripts/start-all.sh.
#
# On Windows `java` is usually the javapath launcher, which spawns the real JVM as a child, so a
# PID file may point at the launcher instead of the process holding the port. Therefore the port
# owner is authoritative here and the PID files are only a fallback (killed together with their
# children).
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$ROOT/logs}"

# reverse start order: gateway -> booking -> hotel -> eureka
SERVICES="api-gateway:8080 booking-service:8082 hotel-service:8081 eureka-server:8761"

kill_pid() {
  local pid=$1
  if command -v taskkill >/dev/null 2>&1; then
    taskkill //F //T //PID "$pid" >/dev/null 2>&1
  else
    kill -9 "$pid" >/dev/null 2>&1
  fi
}

# pid_on_port <port> -> PID of the process listening on the port (empty when the port is free)
pid_on_port() {
  netstat -ano 2>/dev/null | grep 'LISTENING' | grep -E "[:.]$1[[:space:]]" \
    | awk '{print $NF}' | grep -E '^[0-9]+$' | sort -u | head -1
}

for entry in $SERVICES; do
  name="${entry%%:*}"
  port="${entry##*:}"
  pid="$(pid_on_port "$port")"

  if [ -z "$pid" ] && [ -f "$LOG_DIR/$name.pid" ]; then
    pid="$(tr -d '\r\n' <"$LOG_DIR/$name.pid")"
  fi

  if [ -n "$pid" ]; then
    kill_pid "$pid"
    echo "  stopped $name (pid $pid, port $port)"
  else
    echo "  $name is not running (port $port is free)"
  fi
  rm -f "$LOG_DIR/$name.pid"
done

sleep 1
still="$(netstat -ano 2>/dev/null | grep 'LISTENING' | grep -E '[:.](8761|8080|8081|8082)[[:space:]]' || true)"
if [ -n "$still" ]; then
  echo "WARNING: these ports are still in use:" >&2
  echo "$still" >&2
  exit 1
fi
echo "All services stopped."

