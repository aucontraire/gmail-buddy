# Follow-ups

Pre-existing items observed during other work that belong on the backlog. Each is documented with: where it surfaces, why it's not blocking, recommended fix, and recommended timing/branch.

---

## FU-001 — Configure persistent encryption key for `TokenReferenceService`

**Surfaces as**: startup log entry every time the application boots:

```
WARN  - No encryption key configured. Generating temporary key for session.
       Configure 'app.security.token.encryption-key' for production use.
INFO  - AESEncryptionUtil initialized with secure key
```

Origin: `TokenReferenceService` / `AESEncryptionUtil` (added in the Token Security feature, PR #6).

**Why it's not blocking**: token references are created per request and consumed within the same request lifecycle, so an ephemeral per-startup key still functions correctly for token-validation purposes. The risk is **operational, not functional**: any deployment that survives across process restarts (e.g., a daemon, a server, or even a long-lived local instance you don't want to re-authenticate to) sees its in-memory token-reference cache become unreadable on restart, so subsequent requests using cached references would fail until the cache is rebuilt.

**Recommended fix**:
- Add `app.security.token.encryption-key` to `.env.example` with a placeholder.
- Document the format requirement (likely an AES-256 key — verify in `AESEncryptionUtil`).
- Document a generation recipe (e.g., `openssl rand -base64 32`).
- Update the production deployment runbook (when one exists) to require this key be set.
- Consider whether to fail-fast on missing key in non-`dev` profiles, vs. the current warn-and-continue behavior.

**Recommended timing**: separate small PR after the send/draft feature (`001-send-draft-emails`) merges. Out of scope for that branch per spec § Out of Scope (operational hardening is its own concern).

**Owner (per CLAUDE.md)**: `security-auth-specialist` (token security domain) + `spring-boot-config-manager` (the `.env` / `application.properties` plumbing).

---

## FU-003 — `ValidationException` returns HTTP 400 instead of 422

**Surfaces as**: contract drift between `specs/001-send-draft-emails/contracts/api-endpoints.md` (Endpoint 1 error matrix says `400 invalidArgument` from Gmail → `/problems/invalid-recipient` → HTTP **422 Unprocessable Entity**) and the actual production response, which returns HTTP **400 Bad Request**.

Origin: `ValidationException.getHttpStatus()` returns 400 (used by all Bean Validation failures). When the send-message Gmail-error mapping helper (`mapGmailSendError`) wraps a Gmail-side `invalidArgument` rejection into `ValidationException`, `GlobalExceptionHandler.handleValidationException` resolves the status to 400 — same as a generic input-validation failure. The contract intends 422 for the semantic-rejection-by-mailbox-provider case, distinguishable from 400 for malformed request input.

**Why it's not blocking**: the response is still actionable for the calling service — the ProblemDetail `type` URI distinguishes `/problems/invalid-recipient` from `/problems/validation-error`, so a sophisticated client can branch correctly. But the HTTP status semantics don't match the contract.

**Recommended fix**:
- Add a new `InvalidRecipientException extends GmailBuddyClientException` with `getHttpStatus() = 422`.
- Update `GmailRepositoryImpl.mapGmailSendError(...)` to throw `InvalidRecipientException` (instead of `ValidationException`) for the `invalidArgument` case from Gmail's send/draft endpoints.
- Add a dedicated `@ExceptionHandler(InvalidRecipientException.class)` branch in `GlobalExceptionHandler` returning 422 + `/problems/invalid-recipient`.
- Update the SendMessageControllerTest assertion to expect 422 (currently asserts 400 to match observed behavior).

**Recommended timing**: small follow-up commit on the same branch IF you want the contract to match before merging. Otherwise a focused follow-up PR after merge — the test asserts current behavior so this isn't a regression.

**Owner (per CLAUDE.md)**: `validation-error-handler` (exception hierarchy + GlobalExceptionHandler) with light coordination from `gmail-api-integration` (the mapGmailSendError helper).

---

## FU-006 — Decide tracking policy for `CLAUDE.md` and `docs/Gmail-Buddy-API.postman_collection.json`

**Surfaces as**: both files are present in the working tree, are updated whenever the API surface changes (most recently in PR #9 by tasks T056 and T057), but are kept untracked per the established branch convention. They drift from production over time without ever being committed.

**Why it's a real concern**: documentation drift. `CLAUDE.md` is the contract for any future Claude Code session in this repo — if its API endpoint list lags behind reality, future sessions will reason from outdated information. `Postman` collection drift means PR reviewers and integration testers don't have an up-to-date reference.

**Three reasonable options**:

1. **Track both files going forward**: `git add CLAUDE.md docs/Gmail-Buddy-API.postman_collection.json` once, then update + commit them with each feature PR. Pros: always current; PR reviewers see endpoint additions in the diff. Cons: Postman collection diffs are noisy (JSON ID fields change between environments); CLAUDE.md is sometimes used for personal session notes that may not belong in version control.

2. **Track only `CLAUDE.md`**: it's pure markdown and changes are intentional. Leave Postman untracked since its noise outweighs its review value (the OpenAPI annotations + `docs/API_TESTING_GUIDE.md` already cover the same content for reviewers).

3. **Accept the drift**: keep both untracked; commit to a discipline of re-syncing them at the start of every feature branch by running through the spec's contract. Cons: easy to forget; "discipline" is a fragile compliance mechanism.

**Recommended**: option 2 (track `CLAUDE.md`, leave Postman untracked). Rationale: `CLAUDE.md` is small, all-text, and load-bearing for future AI-assisted work in the repo; Postman's JSON noise is real and the same information is captured in tracked `OpenAPI` annotations and `API_TESTING_GUIDE.md`. But this is a project-policy decision, not a clear-cut technical answer — your call.

**Recommended timing**: trivial decision; once made, a small commit either tracks `CLAUDE.md` or doesn't.

**Owner (per CLAUDE.md)**: project maintainer (you) — this is a policy choice, not a domain task.

---

## How to use this doc

- Add new entries as `FU-NNN` sequentially.
- Each entry stays open until the corresponding fix lands; on completion, mark the heading as `~~FU-NNN~~ (resolved in <commit-sha>)` and move to the bottom of the file.
- Items here are intentionally NOT the same scope as feature spec backlogs (which live under `specs/`); these are cross-cutting nits / operational items / pre-existing tech debt observed during feature work.
- Scope guideline: prefer FOLLOW_UPS for items that can ship in 1 focused PR. Larger-scope items (deferred features, multi-step initiatives) live in maintainer-side planning notes outside this repo.

---

## Resolved

Resolved entries are kept here for traceability — heading + 1-line summary + fix reference. Full diagnostic detail at the time of filing is in git history.

### ~~FU-002~~ — Spring `@Autowired` warning on `WebConfig` (resolved in PR #11)

Dropped `@Autowired(required = false)` from the sole constructor and the matching null-guard in `addInterceptors`. `RateLimitInterceptor` is unconditionally `@Component`, so the optionality was dead code. Spring no longer emits the "Inconsistent constructor declaration" warning at startup.

### ~~FU-004~~ — `getMessageBody` log statement leaked message body content (resolved in PR #11)

Replaced `message.toPrettyString()` with `message.getId()` on the `getMessageBody` log line, closing a Constitution VII violation (full Gmail SDK `Message` — including `payload.parts.body.data` — was being logged on every `GET /api/v1/gmail/messages/{id}/body` call). Added a Logback `ListAppender`-based regression test asserting the log emits `messageId=…` and no payload/parts/body fragments.

### ~~FU-005~~ — 2 `@Disabled` tests in `GmailControllerTest` (resolved in PR #12)

Re-enabled both tests by fixing the underlying broken `@SpringBootTest` context. Root cause was deeper than the disable rationales suggested: an inner `@Configuration` class was suppressing full-application-context scanning, so security/exception-handling/message-conversion didn't behave as in production. Switched to `@SpringBootTest(classes = GmailBuddyApplication.class)` + `@ActiveProfiles("test")`, removed the inner config, added `@MockitoBean GmailRepository`, added `jsonPath` assertions on the `MessageListResponse` envelope. Skip count dropped from 2 to 0; the third test in the class (`testGetMessageBody_ResourceNotFoundException`) was previously passing for the wrong reason (Spring's default 404 handler, not `GlobalExceptionHandler`) and now passes correctly.
