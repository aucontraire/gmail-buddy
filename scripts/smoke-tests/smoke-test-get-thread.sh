#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$THREAD_ID" ]; then
  echo "Set THREAD_ID env var first. Get one by running ./smoke-test-list-threads.sh"
  echo "and copying an 'id' value from the JSON response."
  exit 1
fi

URL="http://localhost:8020/api/v1/gmail/threads/$THREAD_ID"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .` (unescapes \r\n in HTML email bodies)
#   SUMMARY=1  compact projection — drop body+snippet from messages, keep headers + attachment counts
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{threadId, labelIds, messageCount: (.messages|length), messages: [.messages[] | {id, headers, labelIds, attachmentCount: (.attachments|length)}]}'
  else
    echo "$B" | jq .
  fi
else
  curl -i -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN"
fi

# Expected response (success):
#   HTTP/1.1 200 OK
#   Body: {
#     "threadId": "<same as THREAD_ID>",
#     "labelIds": ["INBOX", "UNREAD", "Label_42", ...]   (UNION across all messages),
#     "messages": [
#       {
#         "id": "...",
#         "threadId": "...",
#         "headers": {                                    (max 9 RFC 5322 names)
#           "From": "...",
#           "To": "...",
#           "Subject": "...",
#           "Date": "...",
#           "Message-ID": "<...>",
#           "References": "<...>"                         (only if present)
#         },
#         "snippet": "...",
#         "body": "<p>...</p>" or "...",
#         "bodyType": "html" | "text",
#         "labelIds": ["INBOX", ...],
#         "attachments": [{"attachmentId":"...","filename":"...","mimeType":"...","sizeBytes":...}, ...]
#       },
#       ... (chronological ascending order — oldest first)
#     ]
#   }
#
# Expected response (thread not found):
#   HTTP/1.1 404 Not Found
#   Content-Type: application/problem+json
#   Body: {"type":"/problems/resource-not-found","title":"Resource Not Found",...}
#
# Expected response (malformed ID — non-hex or >32 chars):
#   HTTP/1.1 400 Bad Request
#   Content-Type: application/problem+json
#
# Headers to also check:
#   X-Gmail-Quota-Used: 10   (single users.threads.get with format=FULL)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. Open the conversation in Gmail; the message count and order match
#      `messages.length` and the chronological sequence of `messages`
#   2. The 9 whitelisted headers appear ONLY for headers actually present on
#      each message (e.g., a message without Cc has no "Cc" key in headers)
#   3. Non-whitelisted headers (Received, Authentication-Results, X-Mailer)
#      are absent from every message's `headers` map
#   4. `labelIds` at the thread level is the SET UNION across all messages
#      (e.g., if msg A has [INBOX,UNREAD] and msg B has [INBOX,Label_42],
#      thread.labelIds is [INBOX,UNREAD,Label_42] in insertion order)
