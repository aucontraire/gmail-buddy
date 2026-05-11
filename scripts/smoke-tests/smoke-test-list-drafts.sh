#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

# Optional: override page size (default 25, max 50). Set PAGE_TOKEN to fetch a
# subsequent page; copy nextPageToken from the response of the prior call.
LIMIT="${LIMIT:-5}"
PAGE_TOKEN_PARAM=""
if [ -n "$PAGE_TOKEN" ]; then
  PAGE_TOKEN_PARAM="&pageToken=$PAGE_TOKEN"
fi

curl -i -X GET "http://localhost:8020/api/v1/gmail/drafts?limit=$LIMIT$PAGE_TOKEN_PARAM" \
  -H "Authorization: Bearer $TOKEN"

# Expected response:
#   HTTP/1.1 200 OK
#   Body: {
#     "results": [
#       {"id":"...","to":[...],"cc":[],"bcc":[],"subject":"...","snippet":"...","threadId":null,"attachmentCount":0},
#       ...
#     ],
#     "nextPageToken": "..." (or null on last page),
#     "totalCount": 12
#   }
#
# Empty queue (no pending drafts):
#   HTTP/1.1 200 OK   (NOT 404)
#   Body: {"results":[],"nextPageToken":null,"totalCount":0}
#
# Headers to also check:
#   X-Gmail-Quota-Used: <1 + N*5>   (variable: 1 list + 5 per item enriched)
#                                    e.g., 5 items returned → 26 units
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. The draft IDs in `results` match what's visible in the Drafts folder
#   2. Sent drafts (created via smoke-test-send-draft.sh) are ABSENT from results
#      (FR-001a — list is the source of truth for "what's pending")
