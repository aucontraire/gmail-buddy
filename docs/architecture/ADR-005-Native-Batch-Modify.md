# ADR-005: Native Batch Modify for Trash/Untrash/Label Operations

**Status:** Accepted
**Date:** 2026-07-31
**Sprint:** WI-1 (006-native-batchmodify)
**Relates to:** ADR-003 Performance Crisis Recovery - Native Batch Operations, ADR-004 API Response Standardization

## Context

### Per-Message Quota Cost in the Batch-by-ID Endpoints

Gmail Buddy exposes three batch-by-ID endpoints that operate on an explicit list of message IDs:

- `POST /api/v1/gmail/messages/batchTrash`
- `POST /api/v1/gmail/messages/batchUntrash`
- `POST /api/v1/gmail/messages/batchModifyLabels`

Prior to this change, all three were implemented as a loop of individual `users.messages.modify()` calls inside `GmailBatchClient`. Each call costs **~5 Gmail API quota units per message**, so a batch of 500 messages cost ~2,500 units — the same class of problem ADR-003 solved for permanent delete via `users.messages.batchDelete`. Gmail also exposes a native `users.messages.batchModify` endpoint for exactly this shape of operation (add/remove label IDs across many message IDs in one call), but it had not yet been adopted for these three endpoints.

### The All-or-Nothing Tradeoff

Unlike the per-message loop — where each message succeeds or fails independently — Gmail's native `batchModify` is a single API call covering up to 1000 message IDs, and a failure response covers the *whole chunk*, not an individual ID. Naively adopting the native endpoint would mean one bad or inaccessible message ID could sink an entire chunk's worth of otherwise-valid IDs, silently regressing the truthful per-ID success/failure reporting that `BulkOperationResult` and `BatchOperationResponse` are built around (see ADR-004 for the response contract these endpoints share with the rest of the API).

The core design question was therefore: how to get the native endpoint's flat per-call quota cost without giving up per-ID failure fidelity or an all-or-nothing user experience.

## Decision

### 1. Native `batchModify` as the Primary Path

`batchTrash`, `batchUntrash`, and `batchModifyLabels` now call Gmail's native `users.messages.batchModify` as the primary execution path for each chunk, instead of looping `messages.modify()` per message. `batchTrash`/`batchUntrash` are implemented as label modifications (add/remove `TRASH`) through the same native primitive as `batchModifyLabels`.

### 2. Failure Classification Rule

A chunk-level failure from the native call is classified by HTTP status before deciding how to handle it:

- **Transient** (HTTP 429, HTTP 5xx, or a transport-level `IOException` with no HTTP status): rethrown so the existing `executeBatchWithRetry` exponential-backoff/retry wrapper (per ADR-003 — 4 attempts, 2s initial backoff, 2.5x multiplier, 60s cap) retries the *whole native call*. On retry exhaustion, the caller returns retryable per-ID failure reasons for every ID in the chunk — it never falls back to per-message execution.
- **Non-transient** (any other 4xx, e.g. an invalid or inaccessible message ID): the chunk falls back to a **per-message recovery pass** — `executeBatchModifyLabelsPerMessage` re-executes the chunk one message at a time via `messages.modify()` — so a single bad ID does not sink the valid IDs in that chunk. This recovery pass is idempotent.

This mirrors, at the label-modify level, the same transient/non-transient distinction ADR-003's circuit breaker and retry logic already apply to batch delete.

### 3. Contract Invariance

`BatchOperationResponse` is byte-identical regardless of which path (native success, native-retry success, or per-message recovery) produced the result. Callers cannot observe which path ran — only the truthful per-ID success/failure outcome, consistent with the RFC 7807 / response-standardization work in ADR-004.

### 4. Cap Decoupling (K1)

The batch-by-ID input cap was split from the permanent-delete cap:

```properties
# Permanent-delete filter-query page cap (unchanged)
gmail-buddy.gmail-api.batch-delete-max-results=500

# By-ID batch input ceiling (trash/untrash/modify-labels) — decoupled from the
# permanent-delete cap above so raising this never widens the permanent-delete
# blast radius (FR-009).
gmail-buddy.gmail-api.batch-modify-max-results=1000
```

Before this change, the batch-by-ID input limit was tied to the same configuration as the permanent-delete page cap. Because native `batchModify` supports up to 1000 IDs per call (double Gmail's `batchDelete` chunk limit), keeping a single shared cap meant any increase to the modify-side limit would also increase how many messages a single permanent-delete filter query could sweep up. `batch-modify-max-results` is a new, independently configurable property (default 1000) enforced in `GmailService.validateBatchSize()`, so operators can tune the two operations' blast radii separately — raising the reversible batch-modify cap carries none of the risk of raising the irreversible batch-delete cap.

## Consequences

### Positive

- **Quota reduction for these three endpoints**: native `batchModify` costs a flat **~50 quota units per API call**, regardless of chunk size (up to 1000 IDs), versus **~5 units per message** under the old per-message loop.
- **Per-ID failure fidelity preserved**: the per-message recovery pass means a single invalid ID in a chunk no longer causes valid IDs in that chunk to be reported as failed.
- **No client-visible contract change**: `BatchOperationResponse` shape is identical across the native, native-retry, and per-message-recovery paths.
- **Blast-radius isolation**: the K1 cap decoupling (FR-009) means the batch-modify cap can be raised independently of the batch-delete cap.
- **Live-validated**: a 611-message live smoke test on 2026-07-31, in both directions (execute and restore), completed with 0 failures.

### Negative / Known Limitation (Follow-up: WI-2)

The adaptive batch-sizing algorithm introduced in ADR-003 (P0-5) — starting at 15 operations per batch, growing by 1 on success up to a ceiling, shrinking aggressively on failure — was retained unchanged for these three endpoints. That algorithm was tuned for a per-message-cost world where smaller batches meant proportionally smaller quota spend. Under native `batchModify`'s flat per-call fee, the algorithm's ceiling (currently keeping observed native chunks in the ~50–83 message range) means a given operation runs as more, smaller native calls than necessary:

- A 500-message operation currently runs as ~10 native calls of ~50 messages each = ~500 quota units.
- The same operation, chunked at the native endpoint's actual 1000-ID limit, would run as 1 call = ~50 quota units.

The realized quota win from this change is therefore **~5x** versus the old per-message path, not the **~50x** the flat per-call fee makes theoretically available. Simplifying or removing the adaptive-sizing layer for the native `batchModify` path — since it no longer needs to hedge against a per-message cost that no longer applies — is a recorded fast-follow (WI-2), not part of this decision.

### Verification Note

Unit and integration tests mock the Gmail API client, so they verify the failure-classification branching (transient → retry, non-transient → per-message recovery) and the `BatchOperationResponse` contract invariance, but cannot observe real Gmail quota accounting or Gmail's actual native `batchModify` failure semantics. The 611-message live smoke test on 2026-07-31 (execute + restore, 0 failures) is the source of truth that the real-world behavior matches the mocked expectations.

## Alternatives Considered

### Alternative 1: Keep the Per-Message Loop

**Rejected because:** this is the status quo this ADR replaces — ~5 units/message does not scale, and ADR-003 already established the pattern of moving to Gmail's native batch primitives wherever available.

### Alternative 2: Native `batchModify` with No Failure Recovery (Pure All-or-Nothing)

**Description:** Call native `batchModify` and let any chunk failure — transient or not — fail the entire chunk, matching `batchDelete`'s atomic semantics from ADR-003.

**Rejected because:** unlike permanent delete (where an all-or-nothing failure simply means "nothing changed, safe to retry"), a modify/trash/untrash chunk containing one bad ID among hundreds of valid ones would report every ID in that chunk as failed, even though most could have succeeded. This would be a user-visible regression in reliability versus the per-message loop it replaces.

### Alternative 3: Fan Out to Per-Message on Any Failure (Transient or Not)

**Description:** Skip the transient/non-transient distinction and always fall back to per-message recovery on any native `batchModify` failure.

**Rejected because:** transient failures (429/5xx) are expected to succeed on retry of the same native call; falling back to per-message execution on a rate-limit response would multiply the number of API calls made during exactly the condition (rate limiting) that calls for backing off, not fanning out. Retrying the native call preserves the flat-fee cost advantage; per-message fallback is reserved for failures a retry cannot fix.

## Related ADRs

- **ADR-003**: Established the native-batch-endpoint pattern (`batchDelete`), the circuit breaker, exponential backoff, and adaptive batch sizing that this decision builds on and partially supersedes (see Known Limitation above).
- **ADR-004**: Established the response contract (`BatchOperationResponse` / RFC 7807 errors) that this change preserves byte-for-byte across all three execution paths.

## Files Involved

- `src/main/java/com/aucontraire/gmailbuddy/client/GmailBatchClient.java` — native `batchModify` primary path, failure classification, per-message recovery
- `src/main/java/com/aucontraire/gmailbuddy/service/GmailService.java` — `validateBatchSize()` enforcing `batch-modify-max-results`
- `src/main/java/com/aucontraire/gmailbuddy/config/GmailBuddyProperties.java` — `batchModifyMaxResults` property
- `src/main/resources/application.properties` — `gmail-buddy.gmail-api.batch-modify-max-results=1000`

---

**Date Created:** 2026-07-31
**Implementation Status:** Complete — shipped and live-validated at 611-message scale (execute + restore, 0 failures)
**Next Review:** After WI-2 (adaptive-sizing simplification for the native path)
