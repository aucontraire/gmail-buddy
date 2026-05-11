#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$DRAFT_ID" ]; then
  echo "Set DRAFT_ID env var first. Get one by running ./smoke-test-create-draft.sh"
  echo "and copying the 'draftId' value from the JSON response."
  exit 1
fi

# WARNING: This sends a real email. Make sure DRAFT_ID points to a draft that
# was created against an inbox YOU own (not a real recruiter).

curl -i -X POST "http://localhost:8020/api/v1/gmail/drafts/$DRAFT_ID/send" \
  -H "Authorization: Bearer $TOKEN"

# Expected response:
#   HTTP/1.1 200 OK   (NOT 201 — this is a state transition, not creation)
#   No Location header
#   Body: {"messageId":"...","threadId":"...","status":"SENT"}
#
# Verify in Gmail:
#   1. The draft is GONE from the Drafts folder
#   2. The message appears in the Sent folder with the same content
#   3. The recipient (you) receives the email in their Inbox
