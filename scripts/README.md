# scripts/

Operator and developer tooling for the gmail-buddy Spring Boot project. These
are NOT part of the application's runtime — they are convenience scripts for
local validation, smoke tests, and one-off ops tasks.

## Layout

```
scripts/
├── README.md                  # this file
└── smoke-tests/               # one shell script per public API endpoint
    ├── smoke-test-*.sh        # see "Smoke tests" below
    └── _downloads/            # gitignored — download endpoint output (created on first run)
```

## Smoke tests

Each `smoke-test-*.sh` script exercises one HTTP endpoint against a locally
running app (`./mvnw spring-boot:run`, port 8020). Scripts are designed for
manual end-to-end verification against a real Gmail account — useful before
opening a PR or after a non-trivial refactor.

### Prerequisites

1. **Local app running**: `./mvnw spring-boot:run` (binds to `http://localhost:8020`)
2. **`TOKEN` env var**: a valid OAuth2 access token with the Gmail scopes the
   project requests (`gmail.readonly`, `gmail.modify`, `mail.google.com`). Get
   one via the [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/):
   - Step 1: select Gmail API v1 → check the scopes above
   - Step 2: authorize, exchange for tokens
   - Step 3: copy `access_token` into your shell:
     ```
     export TOKEN='ya29.a0...'
     ```
   - Tokens expire after ~1h — re-export when calls start returning 401.
3. **`TEST_RECIPIENT` env var** (send-side scripts only): the email address
   that draft/send/threaded smoke tests will use as the recipient. Set it once
   in your local `.env` (git-ignored — see `.env.example` for the template):
   ```
   TEST_RECIPIENT=you@example.com
   ```
   Use an inbox YOU own — these scripts send real emails. The send-side
   scripts source `.env` automatically via `scripts/smoke-tests/_lib.sh`.
4. **`jq`** (used for URL-encoding optional filter params): `brew install jq`.
5. **A real Gmail account** with at least one inbound thread, one user-created
   label, and at least one message with an attachment if you want to exercise
   the full surface.

### Running a script

```bash
# Required: set TOKEN once per shell
export TOKEN='ya29.a0...'

# Send-side (feature 003 — drafts, threading, attachments)
./scripts/smoke-tests/smoke-test-create-draft.sh
./scripts/smoke-tests/smoke-test-list-drafts.sh
DRAFT_ID=r9068... ./scripts/smoke-tests/smoke-test-get-draft.sh
DRAFT_ID=r9068... ./scripts/smoke-tests/smoke-test-update-draft.sh
DRAFT_ID=r9068... ./scripts/smoke-tests/smoke-test-send-draft.sh
./scripts/smoke-tests/smoke-test-send-message.sh
./scripts/smoke-tests/smoke-test-send-with-attachment.sh
./scripts/smoke-tests/smoke-test-threaded-reply.sh
./scripts/smoke-tests/smoke-test-threaded-with-attachment.sh
DRAFT_ID=r9068... ./scripts/smoke-tests/smoke-test-delete-draft.sh

# Read-side (feature 004 — threads, message detail, labels, attachments)
./scripts/smoke-tests/smoke-test-list-threads.sh
THREAD_ID=1976a4bc... ./scripts/smoke-tests/smoke-test-get-thread.sh
MESSAGE_ID=1976a4bc... ./scripts/smoke-tests/smoke-test-get-message.sh
./scripts/smoke-tests/smoke-test-list-labels.sh
LABEL_ID=Label_42 ./scripts/smoke-tests/smoke-test-get-label.sh
MESSAGE_ID=1976a4bc... ./scripts/smoke-tests/smoke-test-list-attachments.sh
MESSAGE_ID=1976a4bc... ATTACHMENT_ID=ANGjdJ8... ./scripts/smoke-tests/smoke-test-download-attachment.sh
```

Most scripts accept additional optional env vars (`LIMIT`, `PAGE_TOKEN`,
`FORMAT`, `FILENAME`, `MIMETYPE`, structured filter params like `FROM`, `TO`,
`SUBJECT`, `QUERY`, `HAS_ATTACHMENT`, etc.). Each script's leading comment
block explains what it accepts and what response shape to expect.

### Output formatting (read-side scripts only)

The 6 JSON-returning read scripts (`list-threads`, `get-thread`, `get-message`,
`list-labels`, `get-label`, `list-attachments`) accept two optional output
modes — useful when the response body is too large or too escaped to read
raw in a terminal. Default behavior is unchanged (`curl -i` raw output).

```bash
# Pretty-print body via `jq .` — unescapes \r\n, indents nested JSON
PRETTY=1 THREAD_ID=19e144e656e37d80 ./scripts/smoke-tests/smoke-test-get-thread.sh

# Compact projection — drops body/snippet from messages, keeps headers + counts
SUMMARY=1 THREAD_ID=19e144e656e37d80 ./scripts/smoke-tests/smoke-test-get-thread.sh
```

Per-script `SUMMARY` projections (each a thoughtful subset for at-a-glance reading):
- `list-threads` → drops snippets, keeps `id` + `historyId` per result + pagination
- `get-thread` → drops body+snippet from each nested message, keeps headers + `attachmentCount` + thread `labelIds`
- `get-message` → drops body+snippet, keeps `id`/`threadId`/`headers`/`labelIds`/`attachmentCount`
- `list-labels` → keeps `id`/`name`/`type` only (drops visibility fields)
- `get-label` → drops visibility + unread breakdowns, keeps `color` + total counts
- `list-attachments` → drops `attachmentId` for visual cleanness, shows `count` + filenames

The download-attachment script doesn't accept these flags — its response is
binary, not JSON.

### What to look at

Each script invokes `curl -i` so the response headers are printed to stdout.
After the curl line, every script has a comment block describing:

- **Expected success response** — status + body JSON shape
- **Expected error responses** — 400 / 404 / 422 (when applicable)
- **Headers to also check** — `X-Gmail-Quota-Used` per-endpoint cost,
  `X-RateLimit-Limit/Remaining/Reset`
- **Verify in Gmail** — manual cross-checks against the Gmail web UI

### When to run them

- Before opening a PR that touches an endpoint, run that endpoint's smoke test
  end-to-end against a real Gmail account
- After a major refactor (auth, mapper, rate-limit), run the full suite
- When adding a new endpoint, add a matching `smoke-test-<endpoint>.sh` here

### What they're NOT

These are NOT a substitute for the JUnit suite (`./mvnw test`). The unit and
integration tests cover validation, error handling, and routing exhaustively
with mocked Gmail interactions. The smoke tests catch the things mocks
can't — real OAuth2 flows, real Gmail behavior, end-to-end MIME parsing,
real attachment binary integrity, header sanitisation against actual HTTP
clients, etc.

## Adding a new script

1. Create `scripts/smoke-tests/smoke-test-<endpoint>.sh`
2. Start with `#!/usr/bin/env bash` + `set -e`
3. Source the shared helper for env loading and presence checks:
   ```bash
   . "$(dirname "$0")/_lib.sh"
   load_env
   require_env TOKEN "Get from https://developers.google.com/oauthplayground/"
   require_env TEST_RECIPIENT "An inbox YOU own. See .env.example."  # only if the script sends mail
   ```
4. Single `curl -i` invocation — no jq pipelines, no hidden side effects
   (the read-side scripts have an optional `PRETTY=1` / `SUMMARY=1` mode —
   add only if a JSON-returning endpoint is verbose enough to need it)
5. Trailing comment block in the same format as existing scripts
6. `chmod +x` it
7. Add a one-line entry to the "Running a script" section above
8. **Never hardcode PII** (email addresses, usernames, real recipient lists,
   etc.). Read everything from `TEST_RECIPIENT` / `TOKEN` / similar env vars
   loaded via `_lib.sh`. Add new vars to `.env.example` so others know they're
   needed; the actual values live in each developer's git-ignored `.env`.
