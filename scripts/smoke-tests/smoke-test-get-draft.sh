#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$DRAFT_ID" ]; then
  echo "Set DRAFT_ID env var first. Get one by running ./smoke-test-list-drafts.sh"
  echo "and copying an 'id' value from the JSON response, OR by running"
  echo "./smoke-test-create-draft.sh and copying the 'draftId' from the response."
  exit 1
fi

curl -i -X GET "http://localhost:8020/api/v1/gmail/drafts/$DRAFT_ID" \
  -H "Authorization: Bearer $TOKEN"

# Expected response (success):
#   HTTP/1.1 200 OK
#   Body: {
#     "id": "...",
#     "to": [...], "cc": [], "bcc": [],
#     "subject": "...",
#     "body": "<p>...</p>",
#     "bodyType": "html",
#     "threadId": "..." (or null),
#     "inReplyToMessageId": "..." (or null),
#     "attachments": [{"filename":"...","mimeType":"...","sizeBytes":...}, ...]
#   }
#
# Expected response (draft not found, sent, or deleted):
#   HTTP/1.1 404 Not Found
#   Content-Type: application/problem+json
#   Body: {"type":"/problems/resource-not-found","title":"Resource Not Found",...}
#
# Expected response (malformed ID — non-hex or >32 chars):
#   HTTP/1.1 400 Bad Request
#   Content-Type: application/problem+json
#
# Headers to also check:
#   X-Gmail-Quota-Used: 5   (single users.drafts.get with format=FULL)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. The returned subject/recipients/body match what's in the Drafts folder
#   2. Attachments list shows correct filenames and sizes (no binary data)
