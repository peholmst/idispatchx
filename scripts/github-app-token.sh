#!/usr/bin/env bash
# Fetches a GitHub App installation access token for a repository.
# Prints the token to stdout; all diagnostic output goes to stderr.
#
# Required environment variables:
#   GITHUB_APP_CLIENT_ID        GitHub App client ID
#   GITHUB_APP_PRIVATE_KEY_FILE Path to the PEM-encoded RSA private key
#
# Optional environment variables:
#   GITHUB_REPO   owner/repo to look up the installation for
#                 (default: peholmst/idispatchx)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${GITHUB_REPO:-peholmst/idispatchx}"
API="https://api.github.com"

jwt=$("$SCRIPT_DIR/github-app-jwt.sh")

github_api() {
    local method="$1"
    local path="$2"
    shift 2
    curl -sf \
        -X "$method" \
        -H "Authorization: Bearer $jwt" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "$@" \
        "${API}${path}"
}

printf 'Fetching installation for %s ...\n' "$REPO" >&2
installation_response=$(github_api GET "/repos/${REPO}/installation")
installation_id=$(printf '%s' "$installation_response" | jq -r '.id')

if [[ -z "$installation_id" || "$installation_id" == "null" ]]; then
    printf 'Error: no installation found for repository %s\n' "$REPO" >&2
    printf 'Response: %s\n' "$installation_response" >&2
    exit 1
fi

printf 'Requesting access token for installation %s ...\n' "$installation_id" >&2
token_response=$(github_api POST "/app/installations/${installation_id}/access_tokens")
token=$(printf '%s' "$token_response" | jq -r '.token')
expires_at=$(printf '%s' "$token_response" | jq -r '.expires_at')

if [[ -z "$token" || "$token" == "null" ]]; then
    printf 'Error: failed to obtain access token\n' >&2
    printf 'Response: %s\n' "$token_response" >&2
    exit 1
fi

printf 'Token obtained (expires %s)\n' "$expires_at" >&2
printf '%s\n' "$token"
