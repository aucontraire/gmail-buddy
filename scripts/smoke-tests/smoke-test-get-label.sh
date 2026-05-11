#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$LABEL_ID" ]; then
  echo "Set LABEL_ID env var first. Get one by running ./smoke-test-list-labels.sh"
  echo "and copying an 'id' value from the JSON response."
  echo "Try INBOX (system) or one of your Label_* (user) labels."
  exit 1
fi

URL="http://localhost:8020/api/v1/gmail/labels/$LABEL_ID"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .`
#   SUMMARY=1  compact projection — drop visibility + unread breakdowns, keep id/name/type/color/totals
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{id, name, type, color, messagesTotal, threadsTotal}'
  else
    echo "$B" | jq .
  fi
else
  curl -i -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN"
fi

# Expected response (success, user label with color set):
#   HTTP/1.1 200 OK
#   Body: {
#     "id": "Label_42",
#     "name": "Recruiters",
#     "type": "user",
#     "messageListVisibility": "show",
#     "labelListVisibility": "labelShow",
#     "color": {"textColor":"#ffffff","backgroundColor":"#16a766"},
#     "messagesTotal": 248,
#     "messagesUnread": 3,
#     "threadsTotal": 102,
#     "threadsUnread": 2
#   }
#
# Expected response (success, system label without color):
#   HTTP/1.1 200 OK
#   Body: {
#     "id": "INBOX",
#     "name": "INBOX",
#     "type": "system",
#     "messageListVisibility": "hide",
#     "labelListVisibility": "labelShow",
#     "color": null,
#     "messagesTotal": 1234, "messagesUnread": 12,
#     "threadsTotal": 567, "threadsUnread": 8
#   }
#
# Expected response (label not found):
#   HTTP/1.1 404 Not Found
#   Body: {"type":"/problems/resource-not-found",...}
#
# Expected response (malformed labelId — contains hyphen, slash, dot, etc.):
#   HTTP/1.1 400 Bad Request
#   (uses @GmailLabelId regex [A-Za-z0-9_]{1,128} — no hyphens permitted in label IDs)
#
# Headers to also check:
#   X-Gmail-Quota-Used: 1   (single users.labels.get)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. messagesTotal/messagesUnread/threadsTotal/threadsUnread match the counts
#      shown next to the label in Gmail's web UI sidebar
#   2. For user-created labels with a color set: textColor + backgroundColor
#      match the color you picked in Gmail's "Edit label" dialog
#   3. messageListVisibility / labelListVisibility match the visibility settings
#      in the "Manage labels" page
