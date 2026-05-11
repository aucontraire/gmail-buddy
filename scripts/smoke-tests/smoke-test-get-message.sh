#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$MESSAGE_ID" ]; then
  echo "Set MESSAGE_ID env var first. Get one from ./smoke-test-get-thread.sh"
  echo "(any message.id inside the messages[] array) or from a Gmail notification."
  exit 1
fi

# Optional: ?format=full (default, 10 quota — body + 9 headers + attachments)
#           ?format=metadata (5 quota — headers + attachments only, body=null)
# Case-insensitive: Full / FULL / metadata / METADATA all valid.
# Anything else (e.g., raw, minimal) returns 400.
FORMAT="${FORMAT:-full}"

URL="http://localhost:8020/api/v1/gmail/messages/$MESSAGE_ID?format=$FORMAT"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .` (unescapes \r\n in HTML email bodies)
#   SUMMARY=1  compact projection — drop body+snippet, keep id/threadId/headers/labelIds/attachmentCount
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{id, threadId, labelIds, headers, attachmentCount: (.attachments|length)}'
  else
    echo "$B" | jq .
  fi
else
  curl -i -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN"
fi

# Expected response (success, format=full):
#   HTTP/1.1 200 OK
#   Body: {
#     "id": "<same as MESSAGE_ID>",
#     "threadId": "...",
#     "headers": {                                       (max 9 RFC 5322 names)
#       "From": "sender@example.com",
#       "To": "you@example.com",
#       "Subject": "Hello",
#       "Date": "Sun, 10 May 2026 12:00:00 -0700",
#       "Message-ID": "<...>",
#       ... (Cc/Bcc/In-Reply-To/References only if present)
#     },
#     "snippet": "Hi there...",
#     "body": "<p>...</p>" or plain text,
#     "bodyType": "html" | "text",
#     "labelIds": ["INBOX","UNREAD",...],
#     "attachments": [{"attachmentId":"...","filename":"...","mimeType":"...","sizeBytes":...}, ...]
#   }
#
# Expected response (success, format=metadata):
#   HTTP/1.1 200 OK
#   Body: same shape EXCEPT:
#     "body": null
#     "bodyType": null
#   (saves 5 quota units; useful when caller already has body cached or doesn't need it)
#
# Expected response (message not found):
#   HTTP/1.1 404 Not Found
#   Body: {"type":"/problems/resource-not-found",...}
#
# Expected response (invalid format value — e.g., ?format=raw):
#   HTTP/1.1 400 Bad Request
#   (Bean Validation @Pattern violation; detail field does NOT echo "raw" back)
#
# Expected response (malformed messageId — non-hex or >32 chars):
#   HTTP/1.1 400 Bad Request
#   (detail field does NOT echo the malformed input back per FR-035a)
#
# Headers to also check:
#   X-Gmail-Quota-Used: 10  (?format=full) OR 5  (?format=metadata)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. Subject/From/To/Date in `headers` exactly match the message in Gmail's UI
#   2. With ?format=metadata, body is null and X-Gmail-Quota-Used drops to 5
#   3. Capture one attachmentId from `attachments` for ./smoke-test-download-attachment.sh
#      (only present if the message has at least one attachment)
