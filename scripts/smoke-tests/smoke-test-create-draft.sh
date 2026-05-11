#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — not a real recipient. See .env.example."

curl -i -X POST "http://localhost:8020/api/v1/gmail/drafts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Phase 3 smoke test",
  "body": "<p>Hi,</p><p>Testing the new draft-creation endpoint via gmail-buddy.</p><p>If this draft is in your Drafts folder, the MVP works.</p>",
  "bodyType": "html"
}
JSON
