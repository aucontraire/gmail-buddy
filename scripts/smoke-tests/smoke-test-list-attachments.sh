#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$MESSAGE_ID" ]; then
  echo "Set MESSAGE_ID env var first. Pick a message that has at least one"
  echo "attachment — find one by browsing ./smoke-test-get-thread.sh output for"
  echo "messages whose 'attachments' array is non-empty."
  echo
  echo "Note: This endpoint is also valid (returns 200 with empty list) for"
  echo "messages that have no attachments — see FR-024 (NOT 404 in that case)."
  exit 1
fi

URL="http://localhost:8020/api/v1/gmail/messages/$MESSAGE_ID/attachments"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .`
#   SUMMARY=1  compact projection — drop attachmentId for visual cleanness, show count
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{count: (.results|length), results: [.results[] | {filename, mimeType, sizeBytes}]}'
  else
    echo "$B" | jq .
  fi
else
  curl -i -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN"
fi

# Expected response (message with N attachments):
#   HTTP/1.1 200 OK
#   Body: {
#     "results": [
#       {"attachmentId":"ANGjdJ8...","filename":"resume.pdf","mimeType":"application/pdf","sizeBytes":245760},
#       {"attachmentId":"ANGjdJ9...","filename":"cover-letter.docx","mimeType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document","sizeBytes":18944},
#       ...
#     ]
#   }
#
# Expected response (message with NO attachments — per FR-024):
#   HTTP/1.1 200 OK   (NOT 404)
#   Body: {"results":[]}
#
# Expected response (message not found):
#   HTTP/1.1 404 Not Found
#   Body: {"type":"/problems/resource-not-found",...}
#
# Expected response (malformed messageId):
#   HTTP/1.1 400 Bad Request
#   (uses @GmailMessageId regex [0-9a-fA-F]{1,32})
#
# Headers to also check:
#   X-Gmail-Quota-Used: 5   (users.messages.get with format=FULL — needed to
#                            walk the MIME part tree; no separate attachment-
#                            metadata endpoint exists in Gmail's API)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. The number of items in `results` matches the number of attachment icons
#      visible at the bottom of the message in Gmail's web UI
#   2. Filenames and MIME types match what Gmail shows
#   3. sizeBytes is the DECODED size (matches the size shown next to each
#      attachment icon in Gmail; NOT the base64-inflated size)
#   4. Capture one `attachmentId` for the next test (./smoke-test-download-attachment.sh)
