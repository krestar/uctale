#!/usr/bin/env bash
set -euo pipefail

FRONTEND_URL="${UCTALE_SMOKE_FRONTEND_URL:-https://uctale.vercel.app}"
BACKEND_URL="${UCTALE_SMOKE_BACKEND_URL:-https://uctale.onrender.com/api/game}"
ACCESS_PASSWORD="${UCTALE_SMOKE_ACCESS_PASSWORD:-}"
ORIGIN="${UCTALE_SMOKE_ORIGIN:-$FRONTEND_URL}"

if [[ -z "$ACCESS_PASSWORD" ]]; then
  echo "[smoke] UCTALE_SMOKE_ACCESS_PASSWORD가 설정되지 않았습니다." >&2
  exit 2
fi

TMP_DIR="$(mktemp -d)"
COOKIE_JAR="$TMP_DIR/cookies.txt"
REQUEST_BODY="$TMP_DIR/verify-password.json"
RESPONSE_HEADERS="$TMP_DIR/headers.txt"
RESPONSE_BODY="$TMP_DIR/body.txt"
trap 'rm -rf "$TMP_DIR"' EXIT
chmod 700 "$TMP_DIR"

curl_common=(
  --silent
  --show-error
  --location
  --connect-timeout 10
  --max-time 45
  --retry 3
  --retry-delay 5
  --retry-all-errors
)

fail() {
  echo "[smoke] 실패: $*" >&2
  exit 1
}

header_value() {
  local name="$1"
  awk -v target="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')" '
    BEGIN { IGNORECASE = 1 }
    {
      line = $0
      sub(/\r$/, "", line)
      split(line, parts, ":")
      key = tolower(parts[1])
      if (key == target) {
        sub(/^[^:]*:[[:space:]]*/, "", line)
        value = line
      }
    }
    END { if (value != "") print value }
  ' "$RESPONSE_HEADERS"
}

assert_cors_headers() {
  local allow_origin allow_credentials
  allow_origin="$(header_value 'Access-Control-Allow-Origin')"
  allow_credentials="$(header_value 'Access-Control-Allow-Credentials')"

  [[ "$allow_origin" == "$ORIGIN" ]] || fail "Access-Control-Allow-Origin 불일치 (expected=$ORIGIN, actual=${allow_origin:-missing})"
  [[ "${allow_credentials,,}" == "true" ]] || fail "Access-Control-Allow-Credentials가 true가 아닙니다."
}

request() {
  : > "$RESPONSE_HEADERS"
  : > "$RESPONSE_BODY"
  curl "${curl_common[@]}" \
    --dump-header "$RESPONSE_HEADERS" \
    --output "$RESPONSE_BODY" \
    --write-out '%{http_code}' \
    "$@"
}

echo "[smoke] 1/4 production frontend 응답 확인"
frontend_status="$(request "$FRONTEND_URL/")"
[[ "$frontend_status" == "200" ]] || fail "frontend가 HTTP 200을 반환하지 않았습니다. (status=$frontend_status)"
grep -Fq '<div id="root"></div>' "$RESPONSE_BODY" || fail "frontend root markup을 찾지 못했습니다."
grep -Fq '<title>UCTale</title>' "$RESPONSE_BODY" || fail "frontend title을 찾지 못했습니다."

echo "[smoke] 2/4 backend CORS preflight 확인"
preflight_status="$(request \
  --request OPTIONS \
  --header "Origin: $ORIGIN" \
  --header 'Access-Control-Request-Method: GET' \
  --header 'Access-Control-Request-Headers: X-UCTale-Client' \
  "$BACKEND_URL/access-session")"
[[ "$preflight_status" =~ ^2[0-9][0-9]$ ]] || fail "CORS preflight가 성공하지 않았습니다. (status=$preflight_status)"
assert_cors_headers

printf '{"password":%s}\n' "$(printf '%s' "$ACCESS_PASSWORD" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')" > "$REQUEST_BODY"
chmod 600 "$REQUEST_BODY"

echo "[smoke] 3/4 공유 접근 세션 발급 확인"
verify_status="$(request \
  --request POST \
  --header "Origin: $ORIGIN" \
  --header 'X-UCTale-Client: web' \
  --header 'Content-Type: application/json' \
  --cookie-jar "$COOKIE_JAR" \
  --data-binary "@$REQUEST_BODY" \
  "$BACKEND_URL/verify-password")"
[[ "$verify_status" == "204" ]] || fail "verify-password가 HTTP 204를 반환하지 않았습니다. (status=$verify_status)"
assert_cors_headers
chmod 600 "$COOKIE_JAR"
grep -q $'\tuctale_access\t' "$COOKIE_JAR" || fail "uctale_access 쿠키가 발급되지 않았습니다."
grep -q $'\tuctale_owner\t' "$COOKIE_JAR" || fail "uctale_owner 쿠키가 발급되지 않았습니다."

echo "[smoke] 4/4 발급된 credential 재사용 확인"
session_status="$(request \
  --request GET \
  --header "Origin: $ORIGIN" \
  --header 'X-UCTale-Client: web' \
  --cookie "$COOKIE_JAR" \
  "$BACKEND_URL/access-session")"
[[ "$session_status" == "204" ]] || fail "access-session이 발급된 credential을 거부했습니다. (status=$session_status)"
assert_cors_headers

echo "[smoke] production frontend/CORS/access-session smoke 통과"
