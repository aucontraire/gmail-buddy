#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$DRAFT_ID" ]; then
  echo "Set DRAFT_ID env var first. Get one by running ./smoke-test-list-drafts.sh"
  echo "or ./smoke-test-create-draft.sh and copying the 'draftId' from the response."
  exit 1
fi

# WARNING: This is a HARD DELETE. Gmail's users.drafts.delete is permanent —
# there is no trash or undo. Make sure DRAFT_ID points to a draft you actually
# want to discard.

curl -i -X DELETE "http://localhost:8020/api/v1/gmail/drafts/$DRAFT_ID" \
  -H "Authorization: Bearer $TOKEN"

# Expected response (success):
#   HTTP/1.1 204 No Content
#   (no response body)
#
# Expected response (already deleted, sent, or never existed):
#   HTTP/1.1 404 Not Found
#   Content-Type: application/problem+json
#   Body: {"type":"/problems/resource-not-found",...}
#
#   (Idempotent semantics per FR-011: callers can treat 404 on DELETE as
#    success-equivalent for cleanup workflows — the draft is "not present"
#    regardless of whether it ever existed.)
#
# Expected response (malformed ID):
#   HTTP/1.1 400 Bad Request
#
# Headers to also check:
#   X-Gmail-Quota-Used: 10   (users.drafts.delete is 10 units)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. The draft is GONE from the Drafts folder
#   2. A follow-up ./smoke-test-get-draft.sh with the same DRAFT_ID returns 404
#   3. A follow-up ./smoke-test-list-drafts.sh does NOT include this id
