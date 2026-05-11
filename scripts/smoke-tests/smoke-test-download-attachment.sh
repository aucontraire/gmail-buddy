#!/usr/bin/env bash
set -e

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN env var first. Get from https://developers.google.com/oauthplayground/"
  exit 1
fi

if [ -z "$MESSAGE_ID" ] || [ -z "$ATTACHMENT_ID" ]; then
  echo "Set MESSAGE_ID and ATTACHMENT_ID env vars first."
  echo "Get both by running ./smoke-test-list-attachments.sh and copying:"
  echo "  - the message ID you used for that call (MESSAGE_ID)"
  echo "  - one 'attachmentId' value from the response (ATTACHMENT_ID)"
  exit 1
fi

# Optional ?filename=&mimeType= query params.
# - filename: rejected (400) if it contains CR / LF / NUL / Unicode line
#   terminators (FR-026a header-injection sanitisation — receive-side analog
#   of @SafeFilename from the send path).
# - mimeType: opaque from the caller; passed through into Content-Type.
# When both omitted, defaults are: filename=attachment, Content-Type=application/octet-stream.
FILENAME="${FILENAME:-attachment.bin}"
MIMETYPE="${MIMETYPE:-application/octet-stream}"

# Where to save the binary content. Default: scripts/smoke-tests/_downloads/<filename>.
OUT_DIR="${OUT_DIR:-$(dirname "$0")/_downloads}"
mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/$FILENAME"

# `-D /dev/stderr` dumps response headers to the terminal (stderr) so you can
# inspect Content-Type / Content-Length / X-Gmail-Quota-Used etc., while
# `-o "$OUT_FILE"` writes ONLY the response body to the file. Mixing
# `-i` with `-o` is a bug — it prepends the HTTP headers to the binary body
# and corrupts the file.
#
# `-w '%{http_code}'` prints the response status to stdout AFTER the transfer
# completes; we capture it so the script can fail visibly on 4xx/5xx (curl
# returns exit 0 for any successful HTTP exchange regardless of status).
# URL-encode FILENAME and MIMETYPE so values with `+`, `/`, spaces, etc. survive
# the query-string round-trip. Without encoding, `image/svg+xml` arrives at the
# server as `image/svg xml` (the `+` is decoded as space per
# application/x-www-form-urlencoded rules) and `MediaType.parseMediaType` rejects
# it. `jq -sRr @uri` does RFC 3986 percent-encoding on the raw string.
FILENAME_ENC=$(printf %s "$FILENAME" | jq -sRr @uri)
MIMETYPE_ENC=$(printf %s "$MIMETYPE" | jq -sRr @uri)
URL="http://localhost:8020/api/v1/gmail/messages/$MESSAGE_ID/attachments/$ATTACHMENT_ID?filename=$FILENAME_ENC&mimeType=$MIMETYPE_ENC"

HTTP_CODE=$(curl -sS -D /dev/stderr -o "$OUT_FILE" -w '%{http_code}' \
  -X GET "$URL" \
  -H "Authorization: Bearer $TOKEN")

echo
echo "HTTP status: $HTTP_CODE"
echo "Saved to: $OUT_FILE"
echo "Bytes written: $(wc -c < "$OUT_FILE")"
if command -v file >/dev/null 2>&1; then
  echo "file(1) sniff: $(file -b "$OUT_FILE")"
fi

# Fail loudly on HTTP errors — the file likely contains an error JSON, not
# the binary you expected. With `set -e` at the top of the script, the next
# line will exit non-zero and the shell will surface it.
case "$HTTP_CODE" in
  2*) ;;  # 200/204 — success
  *)  echo; echo "ERROR: non-2xx response. Open $OUT_FILE to see the error body." >&2; exit 1 ;;
esac

# Expected response (success):
#   HTTP/1.1 200 OK
#   Content-Type: <MIMETYPE>                         (or application/octet-stream)
#   Content-Disposition: attachment; filename="<FILENAME>"
#   Content-Length: <decoded byte count>             (NOT base64-inflated length)
#   X-Content-Type-Options: nosniff                  (always present per FR-026)
#   <binary body — written to $OUT_FILE>
#
# Expected response (message or attachment not found):
#   HTTP/1.1 404 Not Found
#   Body: {"type":"/problems/resource-not-found",...}
#
# Expected response (malformed messageId or attachmentId):
#   HTTP/1.1 400 Bad Request
#   (messageId uses @GmailMessageId; attachmentId uses @GmailAttachmentId
#    regex [A-Za-z0-9_-]{1,1024})
#
# Expected response (FR-026a — filename contains CR/LF/NUL/Unicode line term):
#   HTTP/1.1 400 Bad Request
#   Try: FILENAME=$'inject\r\nContent-Type: text/html'  (header injection attempt)
#   The endpoint MUST reject this BEFORE any Content-Disposition write.
#
# Headers to also check:
#   X-Gmail-Quota-Used: 5   (users.messages.attachments.get is 5 units)
#   X-RateLimit-Limit / Remaining / Reset
#
# Verify locally:
#   1. The saved file opens correctly in the matching application
#      (PDF in a viewer, image in Preview, .docx in Word/Pages, etc.)
#   2. wc -c on the saved file matches the sizeBytes you saw in
#      ./smoke-test-list-attachments.sh for the same attachmentId
#   3. With FILENAME omitted, the saved file is named "attachment.bin" by the
#      OUT_FILE default (server's Content-Disposition would say "attachment");
#      with MIMETYPE omitted, the response Content-Type is application/octet-stream
#   4. Run with a header-injection FILENAME and confirm 400 — gmail-buddy MUST
#      NOT propagate CR/LF into Content-Disposition under any circumstance
#
# Sanity check: file(1) output should match the server's Content-Type, NOT
# `ASCII text, with very long lines, with CRLF line terminators` (that would
# indicate the saved file contains HTTP headers — a script bug, not a server
# bug). If you get an unexpected file(1) verdict, open the file in a hex
# viewer and confirm the magic bytes match the MIME type.
