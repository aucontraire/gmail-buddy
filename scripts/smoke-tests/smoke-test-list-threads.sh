#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

# Optional: override page size (default 50, max 100). Set PAGE_TOKEN to fetch a
# subsequent page; copy nextPageToken from the response of the prior call.
LIMIT="${LIMIT:-5}"

# Optional structured filter params. Combine any subset of these — they're all
# forwarded into a Gmail search query via GmailQueryBuilder.
#   FROM=recruiter@example.com
#   TO=me@example.com
#   SUBJECT="job opportunity"
#   QUERY='label:Recruiters'              # raw Gmail search syntax
#   NEGATED_QUERY='label:Spam'
#   HAS_ATTACHMENT=true
QS="limit=$LIMIT"
[ -n "$PAGE_TOKEN" ]      && QS="$QS&pageToken=$PAGE_TOKEN"
[ -n "$FROM" ]            && QS="$QS&from=$FROM"
[ -n "$TO" ]              && QS="$QS&to=$TO"
[ -n "$SUBJECT" ]         && QS="$QS&subject=$(printf %s "$SUBJECT" | jq -sRr @uri)"
[ -n "$QUERY" ]           && QS="$QS&query=$(printf %s "$QUERY" | jq -sRr @uri)"
[ -n "$NEGATED_QUERY" ]   && QS="$QS&negatedQuery=$(printf %s "$NEGATED_QUERY" | jq -sRr @uri)"
[ -n "$HAS_ATTACHMENT" ]  && QS="$QS&hasAttachment=$HAS_ATTACHMENT"

URL="http://localhost:8020/api/v1/gmail/threads?$QS"

# Output formatting (default = raw curl -i):
#   PRETTY=1   pretty-print body via `jq .` (unescapes \r\n in HTML email bodies)
#   SUMMARY=1  compact projection — drop snippets, keep id/historyId/pagination
if [ -n "${PRETTY}${SUMMARY}" ]; then
  H=$(mktemp -t gmail-buddy-h.XXXX)
  trap 'rm -f "$H"' EXIT
  B=$(curl -sS -D "$H" -X GET "$URL" -H "Authorization: Bearer $TOKEN")
  cat "$H"; echo
  if [ -n "$SUMMARY" ]; then
    echo "$B" | jq '{nextPageToken, totalCount, results: [.results[] | {id, historyId}]}'
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
#       {"id":"<threadId>","snippet":"<gmail-controlled preview>","historyId":"<bigint>"},
#       ...
#     ],
#     "nextPageToken": "..." (or null on last page),
#     "totalCount": 42
#   }
#
# Empty queue (no matching threads):
#   HTTP/1.1 200 OK   (NOT 404)
#   Body: {"results":[],"nextPageToken":null,"totalCount":0}
#
# Validation errors (400):
#   limit=0 / limit=101 / non-integer limit
#   pageToken length > 255 chars
#   any filter param length > 500 chars
#
# Headers to also check:
#   X-Gmail-Quota-Used: 10        (FLAT — single users.threads.list call,
#                                  no per-item enrichment per Clarifications Q1)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify in Gmail:
#   1. Each "id" in `results` corresponds to a conversation visible in the inbox
#   2. With ?from=<addr> set, only threads containing a message FROM that address
#      appear; with ?hasAttachment=true, all returned threads have at least one
#      message with an attachment
#   3. Capture one threadId for the next test (./smoke-test-get-thread.sh)
