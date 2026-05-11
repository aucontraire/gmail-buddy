#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — direct send delivers immediately. See .env.example."

# Generate a tiny test attachment on the fly: a 1-line text file with a unique
# fingerprint string we can verify on the recipient side. Using a text/plain
# attachment keeps the script self-contained (no PDF generator needed).
FINGERPRINT="smoke-test-attachment-$(date +%s)"
ATTACHMENT_TEXT="Hello from gmail-buddy smoke test. Fingerprint: $FINGERPRINT"
ATTACHMENT_B64=$(printf '%s' "$ATTACHMENT_TEXT" | base64)
ATTACHMENT_FILENAME="smoke-test-readme.txt"
ATTACHMENT_MIME="text/plain"

echo "==> Attachment fingerprint (for verification): $FINGERPRINT"
echo "==> Sending..."
echo ""

# WARNING: This sends a real email DIRECTLY (no draft, no review). The recipient
# gets it in their Inbox the moment Gmail accepts the request. Only use TEST_RECIPIENT
# values you control.

curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Attachment smoke test",
  "body": "<p>Hi,</p><p>Testing the attachment path via gmail-buddy. The attached file should contain the fingerprint <code>$FINGERPRINT</code>.</p><p>If the recipient can download <code>$ATTACHMENT_FILENAME</code> and the file contents match the fingerprint, multipart MIME works end-to-end.</p>",
  "bodyType": "html",
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
#   X-Gmail-Quota-Used: 100   <-- attachments do NOT add quota cost; only threading does
#   Body: {"messageId":"...","threadId":"...","status":"SENT"}
#
# Verify in Gmail:
#   1. The recipient's Inbox shows the message with an attachment chip / icon
#      labeled "smoke-test-readme.txt".
#   2. Click the attachment to download. Open it. The contents should be the
#      single line above starting with "Hello from gmail-buddy..." and ending
#      with the fingerprint printed above by this script.
#   3. "Show original" on the message reveals Content-Type: multipart/mixed
#      with boundary=...; one body part text/html, one body part text/plain
#      with Content-Disposition: attachment; filename="smoke-test-readme.txt".
#
# Bonus: to verify the 413 "message too large" path, run with a bogus
# oversized base64 payload (this requires generating ~26 MB of base64 — easier
# to test via a unit test or a manually-crafted curl with a large file).
#
# Bonus: to verify the 400 path-traversal rejection, run with a malicious
# filename:
#
#   curl -i -X POST "http://localhost:8020/api/v1/gmail/messages" \
#     -H "Authorization: Bearer $TOKEN" \
#     -H "Content-Type: application/json" \
#     -d '{"to":["'"$TEST_RECIPIENT"'"],"subject":"x","body":"x","bodyType":"text",
#          "attachments":[{"filename":"../../etc/passwd","mimeType":"text/plain",
#                          "base64Data":"aGk="}]}'
#
# Expected: HTTP 400 with /problems/validation-error and field-level error on
# attachments[0].filename pointing to the @SafeFilename constraint violation.
