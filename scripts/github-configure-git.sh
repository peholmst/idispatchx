#!/usr/bin/env bash
# Configures the git remote to use a fresh GitHub App installation token.
# Run this before any git operation that requires GitHub authentication.
#
# Required environment variables:
#   GITHUB_APP_CLIENT_ID        GitHub App client ID
#   GITHUB_APP_PRIVATE_KEY_FILE Path to the PEM-encoded RSA private key
#
# Optional environment variables:
#   GITHUB_REPO   owner/repo (default: peholmst/idispatchx)
#   GIT_REMOTE    remote name (default: origin)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${GITHUB_REPO:-peholmst/idispatchx}"
REMOTE="${GIT_REMOTE:-origin}"

token=$("$SCRIPT_DIR/github-app-token.sh")

# Embed the token in the remote URL using the x-access-token convention.
# The username is literal "x-access-token"; the token is the password.
authenticated_url="https://x-access-token:${token}@github.com/${REPO}.git"

git remote set-url "$REMOTE" "$authenticated_url"
printf 'Remote "%s" configured with a fresh app token.\n' "$REMOTE" >&2
