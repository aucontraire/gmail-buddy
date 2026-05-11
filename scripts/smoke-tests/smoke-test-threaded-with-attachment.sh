#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — the threaded reply with attachment lands in YOUR Gmail. See .env.example."

if [ -z "$ORIGINAL_MSG_ID" ] || [ -z "$THREAD_ID" ]; then
  echo "Set ORIGINAL_MSG_ID and THREAD_ID env vars first. Get both from:"
  echo ""
  echo "  curl -s -H \"Authorization: Bearer \$TOKEN\" \\"
  echo "    'http://localhost:8020/api/v1/gmail/messages/latest' | jq '.messages[0]'"
  echo ""
  echo "Then:"
  echo "  export ORIGINAL_MSG_ID=<id-from-response>"
  echo "  export THREAD_ID=<threadId-from-response>"
  exit 1
fi

# Generate a tiny test attachment with a unique fingerprint so we can verify
# byte-for-byte fidelity end-to-end.
FINGERPRINT="threaded-with-attachment-$(date +%s)"
ATTACHMENT_TEXT="Hello from gmail-buddy. Threaded reply WITH attachment. Fingerprint: $FINGERPRINT"
ATTACHMENT_B64=$(printf '%s' "$ATTACHMENT_TEXT" | base64)
ATTACHMENT_FILENAME="threaded-readme.txt"
ATTACHMENT_MIME="text/plain"

echo "==> Attachment fingerprint (for verification): $FINGERPRINT"
echo "==> Replying in thread: $THREAD_ID"
echo "==> Original message: $ORIGINAL_MSG_ID"
echo "==> Sending..."
echo ""

curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Re: Threading + attachment smoke test",
  "body": "<p>Hi, brother. I'm just testing my gmail-buddy Java application on a threaded email. Ignore this. And thanks for the updated SVG.</p><p>Following up — testing gmail-buddy's combined threading + attachment path.</p><p>This message should appear nested under the original conversation in Gmail AND have a downloadable attachment <code>$ATTACHMENT_FILENAME</code> containing fingerprint <code>$FINGERPRINT</code>.</p><p>If both conditions hold, US3 (compose) works end-to-end.</p>",
  "bodyType": "html",
  "threadId": "$THREAD_ID",
  "inReplyToMessageId": "$ORIGINAL_MSG_ID",
  "attachments": [
    {
      "filename": "$ATTACHMENT_FILENAME",
      "mimeType": "$ATTACHMENT_MIME",
      "base64Data": "$ATTACHMENT_B64"
    }
  ]
}
JSON

# Expected response:
#   HTTP/1.1 201 Created
#   Location: /api/v1/gmail/messages/{messageId}/body
#   X-Gmail-Quota-Used: 105   <-- threading lookup (5) + send (100); attachments add no quota
#   Body: {"messageId":"...","threadId":"...","status":"SENT"}
#
# Verify in Gmail (BOTH must hold for US3 to be fully working):
#   1. THREADING: the recipient's Inbox shows the message NESTED inside the
#      original conversation thread (not as a separate cold email).
#   2. ATTACHMENT: the message has a downloadable attachment chip labeled
#      "threaded-readme.txt". Click → download → contents include the
#      fingerprint string this script printed at the top.
#   3. SHOW ORIGINAL on the new message reveals BOTH:
#        - In-Reply-To / References headers pointing at the original
#        - Content-Type: multipart/mixed with body part + attachment part
#
# Headers to also check:
#   X-Gmail-Quota-Used: 105   (threading cost applies; attachments are free)
#   X-RateLimit-Remaining: <decreased by 105>
