#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — the threaded reply lands in YOUR Gmail. See .env.example."

if [ -z "$ORIGINAL_MSG_ID" ] || [ -z "$THREAD_ID" ]; then
  echo "Set ORIGINAL_MSG_ID and THREAD_ID env vars first. Get both from:"
  echo ""
  echo "  curl -s -H \"Authorization: Bearer \$TOKEN\" \\"
  echo "    'http://localhost:8020/api/v1/gmail/messages/latest' | jq '.messages[0]'"
  echo ""
  echo "Then:"
  echo "  export ORIGINAL_MSG_ID=<id-from-response>"
  echo "  export THREAD_ID=<threadId-from-response>"
  echo ""
  echo "Pick a message YOU own — this smoke test sends a follow-up that lands in"
  echo "that message's thread, so the recipient (default: your own inbox) will see"
  echo "the new message nested under the original conversation."
  exit 1
fi

curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Re: Threading smoke test",
  "body": "<p>Thanks brother</p>",
  "bodyType": "html",
  "threadId": "$THREAD_ID",
  "inReplyToMessageId": "$ORIGINAL_MSG_ID"
}
JSON

# Expected response:
#   HTTP/1.1 201 Created
#   Location: /api/v1/gmail/messages/{messageId}/body
#   X-Gmail-Quota-Used: 105   <-- threading lookup (5) + send (100)
#   Body: {"messageId":"...","threadId":"...","status":"SENT"}
#
# Verify in Gmail:
#   1. The recipient's Inbox shows the message NESTED inside the original thread
#      (not as a separate cold email).
#   2. "Show original" on the new message reveals:
#        In-Reply-To: <original-rfc-message-id@mail.gmail.com>
#        References: <original-rfc-message-id@mail.gmail.com>
#   3. The conversation count on the original thread increments by 1.
#
# Headers to also check:
#   X-Gmail-Quota-Used: 105   (threaded send is 105 units; non-threaded would be 100)
#   X-RateLimit-Remaining: <decreased by 105>
#
# Bonus: to verify the 422 "original message not found" error path, run
# directly with a bogus 16-hex-char inReplyToMessageId:
#
#   curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
#     -H "Authorization: Bearer $TOKEN" \
#     -H "Content-Type: application/json" \
#     -d '{"to":["'"$TEST_RECIPIENT"'"],"subject":"x","body":"x","bodyType":"text",
#          "inReplyToMessageId":"deadbeefdeadbeef"}'
#
# Expected: HTTP 422 Unprocessable Entity, body type=/problems/original-message-not-found
