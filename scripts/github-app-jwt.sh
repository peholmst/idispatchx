#!/usr/bin/env bash
# Generates a GitHub App JWT (RS256) from a client ID and private key.
#
# Required environment variables:
#   GITHUB_APP_CLIENT_ID        GitHub App client ID
#   GITHUB_APP_PRIVATE_KEY_FILE Path to the PEM-encoded RSA private key

set -euo pipefail

CLIENT_ID="${GITHUB_APP_CLIENT_ID:?GITHUB_APP_CLIENT_ID is required}"
KEY_FILE="${GITHUB_APP_PRIVATE_KEY_FILE:?GITHUB_APP_PRIVATE_KEY_FILE is required}"

if [[ ! -f "$KEY_FILE" ]]; then
    printf 'Error: private key file not found: %s\n' "$KEY_FILE" >&2
    exit 1
fi

b64url() {
    # Standard base64 then convert to URL-safe alphabet and strip padding
    base64 -w 0 | tr '+' '-' | tr '/' '_' | tr -d '='
}

now=$(date +%s)
iat=$((now - 60))   # 60 s in the past to tolerate clock skew
exp=$((now + 540))  # 9 min from now; GitHub allows max 10 min

header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"iat":%d,"exp":%d,"iss":"%s"}' "$iat" "$exp" "$CLIENT_ID" | b64url)

signing_input="${header}.${payload}"
signature=$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign "$KEY_FILE" | b64url)

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
