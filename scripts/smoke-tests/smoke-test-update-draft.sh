#!/usr/bin/env bash
set -e

. "$(dirname "$0")/_lib.sh"
load_env
require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
require_env TEST_RECIPIENT "An inbox YOU own — the updated draft will reflect this recipient. See .env.example."

if [ -z "$DRAFT_ID" ]; then
  echo "Set DRAFT_ID env var first. Get one by running ./smoke-test-create-draft.sh"
  echo "and copying the 'draftId' from the response."
  exit 1
fi

# WARNING: PUT replaces the entire draft. Any previously-set fields you OMIT
# from this body will be CLEARED in the post-update state — including cc, bcc,
# attachments, and threading. To preserve a value, include it explicitly.

curl -i -X PUT "http://localhost:8020/api/v1/gmail/drafts/$DRAFT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<JSON
{
  "to": ["$TEST_RECIPIENT"],
  "subject": "Phase 6 smoke test (updated draft)",
  "body": "<p>Hi,</p><p>This draft was UPDATED via PUT /api/v1/gmail/drafts/{id}.</p><p>If you see this revised text in the Drafts folder, the update endpoint works.</p>",
  "bodyType": "html"
}
JSON

# Expected response (success):
#   HTTP/1.1 200 OK
#   Body: {
#     "id": "<same as DRAFT_ID>",
#     "to": ["..."],
#     "cc": [], "bcc": [],
#     "subject": "Phase 6 smoke test (updated draft)",
#     "body": "<p>Hi,</p>...",
#     "bodyType": "html",
#     "threadId": null,           (was cleared — PUT replaces)
#     "inReplyToMessageId": null,  (was cleared — PUT replaces)
#     "attachments": []            (was cleared — PUT replaces)
#   }
#
# Expected response (draft not found, sent, or deleted):
#   HTTP/1.1 404 Not Found
#   Body: {"type":"/problems/resource-not-found",...}
#
# Expected response (validation failure — bad recipient, header injection, etc.):
#   HTTP/1.1 400 Bad Request   (Bean Validation — same rules as POST /drafts)
#   HTTP/1.1 413 Payload Too Large   (>25 MB total)
#   HTTP/1.1 422 Unprocessable Entity   (Gmail rejects recipient or inReplyTo target missing)
#
# Headers to also check:
#   X-Gmail-Quota-Used: 15   (no threading) OR 20 (with inReplyToMessageId — adds 5 lookup)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. Open the draft in the Drafts folder; subject/body/recipients reflect THIS body
#   2. Run ./smoke-test-get-draft.sh with the same DRAFT_ID; response matches
#   3. The draft ID is UNCHANGED (PUT updates in place; no new ID issued)
#
# To test threading update: add "inReplyToMessageId":"<hex-msg-id>" to the JSON
# body above. The endpoint will trigger a metadata lookup (+5 quota) and set
# In-Reply-To/References headers on the replacement MIME.
