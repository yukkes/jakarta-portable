#!/usr/bin/env bash
#
# Throughput and latency of one running runtime (scripts/portable bench runs it for each).
#
#   bash scripts/bench.sh [base_url]
#
# Both endpoints go through the full stack: JAX-RS filter (JWT verification), CDI, JPA and
# PostgreSQL.
#
#   GET /api/products?page=0&size=20    JPQL -> PostgreSQL
#   GET /api/orders?page=0&size=20      JPQL -> PostgreSQL (orders, then their lines)
#
# wrk -t4 -c50 -d5s --latency after a 10 s warm-up, three runs per endpoint, median reported. The
# warm-up also lets the work some runtimes do right after starting (filling caches) finish.
set -euo pipefail

BASE="${1:-http://127.0.0.1:8080}"
USERNAME="${APP_USER:-admin}"
PASSWORD="${APP_PASSWORD:-admin}"
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-5}"
WARMUP="${WARMUP:-10}"
RUNS="${RUNS:-3}"

command -v wrk >/dev/null || { echo "wrk not found" >&2; exit 1; }

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
# The numbers never travel without the conditions they were measured under.
echo "env       commit=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)" \
    "cpu=$(nproc) ram=$(free -g | awk '/^Mem:/{print $2"G"}')" \
    "load=wrk -t$THREADS -c$CONNECTIONS -d${DURATION}s median-of-$RUNS"

# Right after startup the seeder may still be running, so wait until a login succeeds.
TOKEN=""
for _ in $(seq 30); do
    # `|| true`: a connection error must retry, not abort the script under `set -e`/pipefail.
    TOKEN="$(curl -sS -X POST -H 'Content-Type: application/json' \
        -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
        "$BASE/api/auth/login" 2>/dev/null | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' || true)"
    # if-statement, not `[ ... ] && break`: with `set -e` the short-circuit would abort
    if [ -n "$TOKEN" ]; then break; fi
    sleep 1
done
[ -n "$TOKEN" ] || { echo "cannot log in (check the demo users or APP_ADMIN_PASSWORD)" >&2; exit 2; }

# Guard: the products endpoint must actually return rows. A datasource that silently
# fell back to an empty table makes the "DB" endpoint look much faster than it is.
probe="$(curl -sS -H "Authorization: Bearer $TOKEN" "$BASE/api/products?page=0&size=1")"
if ! printf '%s' "$probe" | grep -q '"code"'; then
    echo "products is empty: load scripts/seed.sql after startup" >&2
    echo "response: $(printf '%s' "$probe" | head -c 200)" >&2
    exit 3
fi

median() { sort -n | awk '{v[NR]=$1} END {printf "%.2f\n", (NR%2) ? v[(NR+1)/2] : (v[NR/2]+v[NR/2+1])/2}'; }

# wrk prints the latency percentiles with a unit (850.00us, 12.34ms, 1.20s). Only the line that
# starts with "99%" is the percentile: the Thread Stats lines end in "+/- Stdev" values such as
# "89.99%", and matching those as well mixed a second number into the median.
p99_ms() {
    awk '$1 == "99%" {
        v = $2; n = v + 0
        if (v ~ /us$/) n = n / 1000; else if (v ~ /ms$/) n = n; else if (v ~ /s$/) n = n * 1000
        printf "%.2f\n", n
    }'
}

measure() { # name path
    local name="$1" path="$2"
    wrk -t"$THREADS" -c"$CONNECTIONS" -d"${WARMUP}s" -H "Authorization: Bearer $TOKEN" \
        "$BASE$path" >/dev/null
    local rps=() p99=()
    for _ in $(seq "$RUNS"); do
        local out
        out="$(wrk -t"$THREADS" -c"$CONNECTIONS" -d"${DURATION}s" --latency \
            -H "Authorization: Bearer $TOKEN" "$BASE$path")"
        rps+=("$(printf '%s' "$out" | awk '/Requests\/sec:/ {print $2}')")
        p99+=("$(printf '%s\n' "$out" | p99_ms)")
    done
    printf '%-14s %9s req/s  p99 %6s ms\n' "$name" \
        "$(printf '%s\n' "${rps[@]}" | median)" "$(printf '%s\n' "${p99[@]}" | median)"
}

echo "== $BASE (wrk -t$THREADS -c$CONNECTIONS -d${DURATION}s, median of $RUNS)"
measure "products" "/api/products?page=0&size=20&sort=code,asc"
measure "orders"   "/api/orders?page=0&size=20&sort=id,desc"
