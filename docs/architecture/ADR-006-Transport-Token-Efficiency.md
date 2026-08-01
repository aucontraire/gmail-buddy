# ADR-006: Transport & Token Efficiency

**Status:** Accepted
**Date:** 2026-08-01
**Sprint:** WI-2 (007-transport-token-efficiency)
**Relates to:** ADR-002 OAuth2 Security Context Decoupling, ADR-003 Performance Crisis Recovery, ADR-005 Native Batch Modify

## Context

### Redundant Per-Request Overhead on `/api/v1/gmail/**`

ADR-005 (WI-1) closed the per-message quota gap for the batch-by-ID endpoints but flagged a follow-up: even with native `batchModify` in place, every authenticated request to `/api/v1/gmail/**` was paying two kinds of overhead that had nothing to do with quota units and everything to do with transport and token-validation cost per call.

**1. A fresh transport (and cold TLS handshake) on every request.** `GmailClient.createGmailService(...)` called `GoogleNetHttpTransport.newTrustedTransport()` on every request, constructing a brand-new SSL/transport instance each time. The JDK's HTTP keep-alive connection cache is keyed by `SSLSocketFactory` identity, so a fresh transport per call defeated connection reuse outright — every request paid a cold TLS handshake to `googleapis.com`, regardless of how recently the previous request had connected to the same host.

**2. Two live Google `tokeninfo` round-trips per authenticated request.** `TokenAuthenticationFilter` validated the incoming bearer token once against Google's live `tokeninfo` endpoint. Then, deeper in the call, `OAuth2TokenProvider.getTokenFromContext()` re-validated the *same raw header token* a second time — a second live call — and only fell through to the encrypted token-reference lookup afterward. Because the re-read-the-header path ran first, the encrypted token-reference cache built under ADR-002's decoupling work was effectively dead on the hot path: an already-authenticated `ROLE_API_USER` request never benefited from it.

Neither problem changed the API contract or the quota-unit accounting from ADR-003/ADR-005 — they were pure latency and Google-side call-volume overhead layered on top of every request, independent of whether that request was a single-message call or a native batch call.

## Decision

Three coordinated changes, shipped together as WI-2, with no API contract change, no new external dependency, and no scope change to any endpoint's behavior.

### 1. Shared Pooled `ApacheHttpTransport` (US1)

Replace the per-request `GoogleNetHttpTransport.newTrustedTransport()` call with a single, application-scoped `@Bean HttpTransport` (`GoogleTransportConfig`) backed by an Apache HttpClient `PoolingHttpClientConnectionManager`:

```properties
gmail-buddy.gmail-api.http-transport.max-per-route=16
gmail-buddy.gmail-api.http-transport.max-total=20
gmail-buddy.gmail-api.http-transport.validate-after-inactivity-ms=2000
gmail-buddy.gmail-api.http-transport.connection-ttl-ms=60000
```

- **Validate-on-borrow + eviction, not a bare shared transport.** A connection idle longer than `validate-after-inactivity-ms` is probed before reuse and discarded if the server has closed it; a background evictor sweeps idle/expired connections on the same threshold, and `connection-ttl-ms` bounds how long any pooled connection may live regardless of use. This is the reason `ApacheHttpTransport` (via `PoolingHttpClientConnectionManager`) was chosen over a bare shared `NetHttpTransport`: the JDK keep-alive cache has no validate-on-borrow step and can hand back a connection the server already closed, producing connection-reset failures under exactly the reuse this change is trying to enable.
- **Important implementation nuance:** `google-http-client-apache-v2:2.0.0` wraps Apache HttpClient **4.x** (`org.apache.http.*`) — it is not built on HttpClient 5. An `httpclient5` jar present on the classpath is an unrelated transitive dependency and is not what backs this transport. All Apache HttpClient types are confined to `GoogleTransportConfig`; the rest of the application depends only on the Google `HttpTransport` abstraction.
- **Pool depth is a co-requisite, not an isolated tuning choice.** `max-per-route=16` / `max-total=20` is sized to Gmail's rate-limit-bound gate concurrency (~250 quota-units/s ÷ ~5 units/read ≈ 50 reads/s ≈ 16 concurrent in-flight reads) and is paired with buzonero's WI-4b concurrency raise (8 → 16 concurrent requests) — this pool depth is what makes that concurrency raise safe rather than merely nominal.
- **Token isolation is preserved.** The shared transport carries no per-caller state. The OAuth2 access token still rides the per-request `HttpRequestInitializer` in `GmailClient`, never the transport itself, so a shared pool does not create any cross-request token leakage risk (FR-002).

### 2. Eliminate the Redundant Second Validation (US2)

`OAuth2TokenProvider.getTokenFromContext()` was reordered: for an already-authenticated `ROLE_API_USER` request, it now obtains the access token via the encrypted token-reference path (`TokenReferenceService`) **first**, with no re-read, re-trust, or re-validation of the raw `Authorization` header token. The header-revalidation path is retained only as a fallback for contexts that are not `ROLE_API_USER`.

This is a direct extension of ADR-002's decoupling work, not a deviation from it — `TokenAuthenticationFilter` remains the single point that performs the authoritative live validation and establishes the security context; `OAuth2TokenProvider` now actually honors that decoupling on the hot path instead of quietly re-doing the filter's work. Net effect: two live `tokeninfo` validations per authenticated request drops to one, and the previously-dead token-reference cache is now live.

### 3. Validation Memo (US3)

A short-TTL, positive-only cache of successful token validations, `TokenValidationCacheImpl` (interface `TokenValidationCache`), is consulted inside `GoogleTokenValidator.getTokenInfo` before making a live `tokeninfo` call:

```properties
gmail-buddy.gmail-api.token-validation-cache.enabled=true
gmail-buddy.gmail-api.token-validation-cache.ttl-seconds=60
```

- **Key:** `SHA-256(token)` — the raw token is never stored or logged, only its hash.
- **TTL:** `min(60s, token expires_in)` — never caches a validation past the token's own expiry.
- **Storage:** `ConcurrentHashMap` with lazy expiry (checked on read, no background sweep thread).
- **FR-012 gate-safety (the non-negotiable part of this design):** the memo caches and returns the full `TokenInfoResponse`, including `scope` — it does not cache a pass/fail verdict. The caller, `TokenAuthenticationFilter`, still runs `hasValidGmailScopes` against the returned scope on every request, memo hit or not. A memo hit can only save the live HTTP round-trip to Google; it can never bypass the scope gate.
- **Kept distinct from `TokenReferenceService`.** `TokenReferenceService` (ADR-002) owns encrypted raw-token storage for the request lifecycle and has its own fixed-TTL semantics tied to that purpose. Folding the validation memo into it would have coupled two different caches with different correctness requirements (token storage vs. re-validation avoidance) onto one fixed TTL; keeping them separate lets the memo's TTL be driven purely by "how long is a successful validation still trustworthy," independent of how long a token reference needs to live.

## Consequences

### Positive

- **Live `tokeninfo` round-trips: 2 → ≤1 per authenticated request**, and closer to 1 per 60-second window across bursts of requests using the same token (memo hits absorb repeats within the TTL).
- **Pooled TLS connection reuse** replaces a cold handshake on every request — this is also the concurrency enabler for buzonero's WI-4b (8 → 16) raise, since a shared validated pool is what makes higher concurrent request volume safe against stale-connection resets.
- **Zero stale-connection resets** attributable to the shared pool, per the live validation run below.
- **Live-validated 2026-08-01** on a real 1,214-message restore run:
  - 7 requests → 2 live `tokeninfo` validations (vs. 14 under the pre-WI-2 two-call-per-request behavior).
  - 5 memo hits observed, plus one clean 60-second TTL expire → refresh cycle.
  - 68 pooled-connection leases served out of a 20-connection-cap pool (i.e., real reuse, not 68 fresh handshakes).
  - 0 connection resets.
  - 1,214 messages restored, 0 failures.

### Negative / Known Limitation (Follow-up, not part of this decision)

The adaptive batch-sizing algorithm retained from ADR-003/ADR-005 (start at ~15 operations per batch, grow by 1 on success, shrink aggressively on failure) still cold-starts small after every application restart. It fans a native batch operation into many small (~15-message) calls until it warms back up to its steady-state ceiling. This caps the realized *quota* win from ADR-005's native-`batchModify` change independently of WI-2's transport/token work — WI-2 does not touch batch sizing at all. Tracked as a candidate follow-up (WI-9): warm-start the adaptive size from a persisted or configured floor instead of restarting at 15 on every process boot.

### Verification Note

Unit and integration tests mock both the Google validation call (`RestTemplate`) and the Gmail API client (`GmailClient`); they prove the call-count reduction, the memo's cache/expiry/scope-gate behavior, and token isolation under the shared transport, but they cannot observe real TLS connection reuse, real pool exhaustion behavior, or Google's live token-revocation latency. The 1,214-message live restore run on 2026-08-01 is the source of truth for real transport reuse and real validation-count behavior; the mocked test suite is the source of truth for the branching logic and the FR-012 scope-gate invariant.

## Alternatives Considered

### Alternative 1: Bare Shared `NetHttpTransport` + `-Dhttp.maxConnections`

**Description:** Share a single `NetHttpTransport` across requests and raise the JDK's connection pool size via the `http.maxConnections` system property instead of introducing an Apache-backed pooled transport.

**Rejected because:** the JDK's keep-alive cache has no validate-on-borrow step, so a connection the server has already closed can be handed back to a caller and fail with a connection reset — exactly the failure class this change is meant to eliminate. `http.maxConnections` is also a global, implicit JVM flag (not a Spring-managed, per-application-scoped setting) that silently reverts to its default of 5 if not set correctly at JVM startup, which is a fragile way to guarantee the pool depth WI-4b's concurrency raise depends on.

### Alternative 2: `google-http-client-apache-v5` (Apache HttpClient 5)

**Description:** Use the HttpClient-5-based Google transport adapter instead of the HttpClient-4-based `google-http-client-apache-v2`.

**Rejected because:** it would introduce a new dependency for no functional gain — `google-http-client-apache-v2`'s pooling, validate-on-borrow, and eviction behavior already meet every requirement of this change. Not needed.

### Alternative 3: Trust the Re-Read Header Token, or Share a Validation Memo, to Eliminate Call #2

**Description:** Instead of reordering `OAuth2TokenProvider` to prefer the token-reference path, simply trust the already-filter-validated header token without a second live check, or let a shared validation memo silently stand in for the decoupled token-reference lookup.

**Rejected because:** either approach would have `OAuth2TokenProvider` reach back into the raw request/header token directly, re-introducing exactly the tight coupling between the repository/service layer and the raw authentication artifact that ADR-002's decoupling work was designed to remove. Routing through `TokenReferenceService` first (US2) achieves the same call-count reduction while keeping the token-reference abstraction as the actual source of truth for an already-authenticated request.

### Alternative 4: Cache a Pass/Fail Validation Verdict Instead of the Full `TokenInfoResponse`

**Description:** Have the validation memo (US3) cache a simple boolean ("this token was valid") instead of the full response including scope.

**Rejected because:** this would let a memo hit bypass `hasValidGmailScopes` — a token that was valid with a narrower scope set at cache time could be waved through on a later request without re-checking scope, silently violating FR-012's requirement that scope be checked on every request. Caching the full `TokenInfoResponse` and re-running the scope check on every access (memo hit or not) was the only design that preserves the gate's guarantee.

## Related ADRs

- **ADR-002**: Established the `TokenProvider` abstraction and the encrypted token-reference path this decision reorders `OAuth2TokenProvider` to actually use on the hot path.
- **ADR-003**: Established the native-batch-endpoint pattern and adaptive batch sizing whose cold-start behavior is called out above as an independent, not-yet-addressed limitation.
- **ADR-005**: Shipped native `batchModify` for the batch-by-ID endpoints (WI-1) and explicitly flagged the adaptive-sizing follow-up (WI-2 origin) that this ADR's Known Limitation section revisits — WI-2 addressed transport/token overhead only, not adaptive sizing.

## Files Involved

- `src/main/java/com/aucontraire/gmailbuddy/config/GoogleTransportConfig.java` — shared pooled `ApacheHttpTransport` bean, connection manager and HTTP client construction
- `src/main/java/com/aucontraire/gmailbuddy/client/GmailClient.java` — consumes the shared transport; per-request `HttpRequestInitializer` still carries the caller's token
- `src/main/java/com/aucontraire/gmailbuddy/service/OAuth2TokenProvider.java` — `getTokenFromContext()` reordered to prefer the token-reference path for `ROLE_API_USER` contexts
- `src/main/java/com/aucontraire/gmailbuddy/security/TokenReferenceService.java` — encrypted token-reference store consulted first (ADR-002)
- `src/main/java/com/aucontraire/gmailbuddy/service/GoogleTokenValidator.java` — consults the validation memo before making a live `tokeninfo` call
- `src/main/java/com/aucontraire/gmailbuddy/service/TokenValidationCache.java` / `TokenValidationCacheImpl.java` — SHA-256-keyed, positive-only, TTL-bounded validation memo
- `src/main/java/com/aucontraire/gmailbuddy/security/TokenAuthenticationFilter.java` — still the sole enforcer of `hasValidGmailScopes` on every request, memo hit or not (FR-012)
- `src/main/java/com/aucontraire/gmailbuddy/config/GmailBuddyProperties.java` — `HttpTransport` and token-validation-cache configuration records
- `src/main/resources/application.properties` — `gmail-buddy.gmail-api.http-transport.*` and `gmail-buddy.gmail-api.token-validation-cache.*` properties

---

**Date Created:** 2026-08-01
**Implementation Status:** Complete — shipped and live-validated at 1,214-message scale (7 requests, 2 live validations, 5 memo hits, 68 pooled connection leases, 0 connection resets, 0 failures)
**Next Review:** After WI-9 (adaptive batch-sizing warm-start, if scheduled)
