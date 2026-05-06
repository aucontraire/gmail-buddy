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

## How to use this doc

- Add new entries as `FU-NNN` sequentially.
- Each entry stays open until the corresponding fix lands; on completion, mark the heading as `~~FU-NNN~~ (resolved in <commit-sha>)` and move to the bottom of the file.
- Items here are intentionally NOT the same scope as feature spec backlogs (which live under `specs/`); these are cross-cutting nits / operational items / pre-existing tech debt observed during feature work.
