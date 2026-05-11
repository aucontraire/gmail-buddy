#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

URL="http://localhost:8020/api/v1/gmail/labels"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .`
#   SUMMARY=1  compact projection — keep id/name/type only (drop visibility fields)
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{totalCount, results: [.results[] | {id, name, type}]}'
  else
    echo "$B" | jq .
  fi
else
  curl -i -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN"
fi

# Expected response:
#   HTTP/1.1 200 OK
#   Body: {
#     "results": [
#       {"id":"INBOX","name":"INBOX","type":"system","messageListVisibility":"hide","labelListVisibility":"labelShow"},
#       {"id":"SENT","name":"SENT","type":"system",...},
#       {"id":"DRAFT","name":"DRAFT","type":"system",...},
#       {"id":"SPAM","name":"SPAM","type":"system",...},
#       {"id":"TRASH","name":"TRASH","type":"system",...},
#       {"id":"IMPORTANT","name":"IMPORTANT","type":"system",...},
#       {"id":"STARRED","name":"STARRED","type":"system",...},
#       {"id":"UNREAD","name":"UNREAD","type":"system",...},
#       {"id":"CATEGORY_PERSONAL","name":"CATEGORY_PERSONAL","type":"system",...},
#       ... (other CATEGORY_* system labels),
#       {"id":"Label_42","name":"Recruiters","type":"user",...},
#       ... (any user-created labels)
#     ],
#     "totalCount": <results.length>
#   }
#
# Note: NO `nextPageToken` field — Gmail's users.labels.list is non-paginated.
# A user with 0 user-labels still gets the system labels (results is rarely empty).
#
# Headers to also check:
#   X-Gmail-Quota-Used: 1   (cheapest endpoint in the new feature)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. Every label visible in the Gmail web UI's left sidebar appears in `results`
#      with the matching id and name
#   2. System labels (INBOX, SENT, etc.) carry type="system"; your custom labels
#      (the ones you created via Gmail UI) carry type="user"
#   3. Capture one labelId for the next test (./smoke-test-get-label.sh).
#      Try a user-created one (Label_*) to see color/counts populated; system
#      labels usually have null color.
