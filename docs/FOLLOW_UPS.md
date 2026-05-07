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

## FU-002 — Resolve Spring `@Autowired` warning on `WebConfig`

**Surfaces as**: startup log entry every time the application boots:

```
WARN - Inconsistent constructor declaration on bean with name 'webConfig':
       single autowire-marked constructor flagged as optional - this constructor
       is effectively required since there is no default constructor to fall
       back to: public com.aucontraire.gmailbuddy.config.WebConfig$$SpringCGLIB$$0(
         com.aucontraire.gmailbuddy.config.RateLimitInterceptor)
```

Origin: pre-existing in `WebConfig.java`. Likely an `@Autowired(required = false)` (or equivalent default-arg pattern) on a constructor that has no fallback default constructor — Spring's warning says: "you marked this optional but I can't actually skip it because there's no other constructor."

**Why it's not blocking**: cosmetic. The bean is constructed correctly; the warning is just noise on every startup.

**Recommended fix** (one of):
- Remove the `@Autowired` annotation entirely if the constructor is the only one (Spring auto-detects single-constructor injection since 4.3 — explicit annotation is redundant).
- Or, if `@Autowired(required = false)` was intentional, refactor the constructor to truly support optional injection (e.g., add a default constructor or a no-arg path).
- Most likely fix: drop the annotation; net change ≈ 1 line.

**Recommended timing**: trivial 1-line commit. Could go in any small docs/cleanup PR (e.g., bundled with the FU-001 fix, or as a one-off chore commit).

Per CLAUDE.md §A3 (Anti-Slop "No Cosmetic-Only Changes"): do NOT bundle this with feature work. Cosmetic-only changes belong in their own commit so feature diffs stay clean.

**Owner (per CLAUDE.md)**: `spring-boot-config-manager` (Spring DI / configuration domain).

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

## FU-004 — Existing `getMessageBody` log statement leaks message body content

**Surfaces as**: a constitution VII violation in pre-existing code, discovered during the T062 spot-check of the send/draft feature's deviation. Not introduced by the send/draft feature, but it's the same kind of issue the deviation is bounded against.

**Location**: `src/main/java/com/aucontraire/gmailbuddy/repository/GmailRepositoryImpl.java`, line 317:

```java
logger.info("Message retrieved: {}", message.toPrettyString());
```

`message.toPrettyString()` serializes the entire Gmail API `Message` object including `payload` → `parts` → `body.data` (base64-encoded body content). This means the existing `GET /api/v1/gmail/messages/{messageId}/body` endpoint logs full email body content on every call.

**Why it's a violation**: Constitution Principle VII states *"OAuth tokens, credentials, email bodies, and PII MUST NOT appear in logs."* The body content of every retrieved message is being logged.

**Why it wasn't blocking the send/draft feature**: this is in a DIFFERENT endpoint (the existing message-retrieval path), not in any new send/draft endpoint. The new code we added doesn't log body content. Per CLAUDE.md §A3 ("No Cosmetic-Only Changes" mixed with feature work), this fix belongs in its own commit/PR.

**Recommended fix** (1 line):

```java
// Before
logger.info("Message retrieved: {}", message.toPrettyString());

// After
logger.info("Message retrieved: messageId={}", message.getId());
```

Optionally also log `getThreadId()` if useful for diagnostics. Do NOT log `toPrettyString()`, `getPayload()`, or anything that traverses to body content.

**Recommended timing**: trivial 1-line follow-up commit. Could be bundled with FU-002 (cosmetic Spring `@Autowired` warning fix) into one tiny chore commit since both are pre-existing-code hygiene.

**Owner (per CLAUDE.md)**: `gmail-api-integration` (Gmail API path) or `security-auth-specialist` (logging-PII concern). Either is appropriate.

---

## FU-005 — Resolve the 2 `@Disabled` tests in `GmailControllerTest`

**Surfaces as**: `GmailControllerTest.java` carries 2 test methods annotated `@Disabled("Legacy test - ...")`. They show up as skips on every test run (`Tests run: 3, Failures: 0, Errors: 0, Skipped: 2`).

**Why it's a real concern**: CLAUDE.md § "Test Integrity Rules (ABSOLUTE - ZERO TOLERANCE)" explicitly forbids `@Disabled` to hide problems:
> NEVER skip tests with `@Disabled` to hide problems - Address the root cause
> The ONLY acceptable actions when tests fail:
>   1. Fix the code if it's wrong
>   2. Fix the test if it's wrong
>   3. Update the test if requirements changed
>   4. Ask the user for clarification if unclear

The 2 `@Disabled` annotations are pre-existing, not introduced by the send/draft feature, but they're a standing CLAUDE.md violation that ships in every commit until they're either fixed or honestly removed.

**Recommended fix**:
1. Read `GmailControllerTest.java` to understand what the 2 disabled tests were originally trying to verify.
2. For each: decide whether the test is fixable (the production code may have changed making the assertions stale → update the test), should be deleted (the behavior under test is no longer relevant → `git rm` the test method), or should escalate to a real bug report (the test is correct but the production code is broken → fix the production code).
3. Whatever the path, the `@Disabled` annotation must go. CLAUDE.md doesn't allow it.

**Recommended timing**: small focused PR after the send/draft feature merges. ~30-min effort once the original intent of each test is understood.

**Owner (per CLAUDE.md)**: `testing-qa-agent` (test integrity is their domain).

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
