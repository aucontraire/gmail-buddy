#!/usr/bin/env bash
# Shared helpers for gmail-buddy smoke tests. Source this file at the top of
# any script that needs .env loading or env-var presence checks:
#
#   . "$(dirname "$0")/_lib.sh"
#   load_env
#   require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
#
# This file is sourced, never executed directly — the leading shebang is for
# editor language detection and shellcheck only.

# load_env — auto-load the project root .env file if it exists, exporting all
# keys it defines. Keeps secrets, PII, and per-developer values (like
# TEST_RECIPIENT) out of script bodies; the .env file is git-ignored, while
# .env.example documents what each script expects to find there.
#
# Resolves the .env path relative to THIS file's directory
# (scripts/smoke-tests/) → project root, so callers don't need to pass paths.
load_env() {
    local lib_dir env_file
    lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    env_file="$lib_dir/../../.env"
    if [ -f "$env_file" ]; then
        set -a
        # shellcheck disable=SC1090
        . "$env_file"
        set +a
    fi
}

# require_env VAR_NAME [hint]  — print a helpful error and exit 1 if VAR_NAME
# is unset or empty. The optional hint goes on its own line.
require_env() {
    local var="$1"
    local hint="${2:-}"
    if [ -z "${!var}" ]; then
        echo "Set $var env var first." >&2
        if [ -n "$hint" ]; then
            echo "  $hint" >&2
        fi
        echo "  Add to .env (recommended for stable values) or export in your shell." >&2
        echo "  See .env.example for the template." >&2
        exit 1
    fi
}
