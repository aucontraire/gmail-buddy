#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — direct send delivers immediately. See .env.example."

# WARNING: This sends a real email DIRECTLY (no draft, no review). The recipient
# gets it in their Inbox the moment Gmail accepts the request. Only use TEST_RECIPIENT
# values you control.

curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Phase 5 smoke test (direct send)",
  "body": "<p>Hi,</p><p>Testing the direct-send endpoint via gmail-buddy.</p><p>If this lands in your Inbox (not Drafts), the trusted-template path works.</p>",
  "bodyType": "html"
}
JSON

# Expected response:
#   HTTP/1.1 201 Created
#   Location: /api/v1/gmail/messages/{messageId}/body
#   Body: {"messageId":"...","threadId":"...","status":"SENT"}
#
# Verify in Gmail:
#   1. NO draft is created in the Drafts folder
#   2. The message appears in the Sent folder
#   3. The recipient receives the email immediately in their Inbox
#
# Headers to also check:
#   X-Gmail-Quota-Used: 100   (direct send is 100 units; draft+send was 10+100)
#   X-RateLimit-Remaining: <decreased>
