# ADR-007: Spring Boot 4 Upgrade

**Status:** Accepted
**Date:** 2026-10-07
**Sprint:** WI-008 (008-spring-boot-4-upgrade)
**Relates to:** ADR-001 Foundation, ADR-002 OAuth2 Security Context Decoupling, ADR-006 Transport & Token Efficiency

## Context

Gmail Buddy ran on Spring Boot 3.4.0 (Spring Framework 6, Spring Security 6.4.1). Staying on the 3.x line left several Dependabot bumps parked behind the old BOM — including a manual `<1.6.0` logback cap — and deferred the move to the current Spring Boot 4 line. Moving to Boot 4 clears those constraints and keeps the platform current.

Spring Boot 4.x is a platform-level change, not a routine bump. It brings Spring Framework 7, Spring Security 7, Jakarta EE 11 / Servlet 6.1 and Jackson 3. Boot 4 also splits `spring-boot-test-autoconfigure` and related modules into per-technology modules, which changes test dependencies and package names. The upgrade has to be done deliberately so that the externally visible contract (the `/api/v1/gmail/**` Bearer API consumed by buzonero) and the CSRF/OAuth2 security posture are preserved.

## Decision

Upgrade to Spring Boot **4.1.1** as WI-008, with no API contract change and no `src/main` behavior change beyond one RestTemplate timeout bean. The project version moves from 0.8.0 to 0.9.0.

### 1. Parent Bump 3.4.0 to 4.1.1

`spring-boot-starter-parent` moves from 3.4.0 to 4.1.1, pulling Spring Framework 7, Spring Security 7, Jakarta EE 11 / Servlet 6.1 and Jackson 3.

### 2. Java 17 Retained

The official Spring Boot 4.0 migration guide requires "Java 17 or later", so no JDK bump is needed. This repository has no dependency with a Java 21 floor: jOOQ is not used, and `spring-boot-starter-data-jpa` / PostgreSQL are commented out in `pom.xml`.

**Fork noted:** reintroducing JPA/PostgreSQL, jOOQ 3.20+ or Testcontainers could force Java 21 or later. If that happens, the JDK floor becomes its own decision. WI-009 (Java 25 + virtual threads, delivered as a container) is the planned next step.

### 3. Jackson 3 and Jackson 2 Coexistence

Spring MVC now runs on Jackson 3. The Gmail Google client (`google-http-client-jackson2`) is built on Jackson 2 and stays there. To keep both working, the BOM-managed `org.springframework.boot:spring-boot-jackson2` compatibility module was added.

DTO serialization is unchanged. This was verified by the controller and contract test suites passing unmodified with respect to JSON assertions.

### 4. springdoc-openapi 2.8.5 to 3.1.1

springdoc-openapi 2.x supports Boot 3 only. 3.1.1 is the line that targets Spring Boot 4.

### 5. RestTemplate Timeout Migration (`SecurityConfig`)

Spring Framework 7 removed `HttpComponentsClientHttpRequestFactory.setConnectTimeout(int)`. The connect timeout is now set on an Apache HttpClient 5 `ConnectionConfig` (`setConnectTimeout(Timeout.ofSeconds(5))`), applied through a `PoolingHttpClientConnectionManager` that is wired into the HTTP client. The read timeout stays on the factory via `setReadTimeout(Duration.ofSeconds(10))`.

Behavior is preserved: **5 s connect / 10 s read** for the `tokeninfo` validation `RestTemplate`. (This is the Apache HttpClient 5 used by Spring's `RestTemplate` factory; it is unrelated to the HttpClient 4.x that backs the Google transport in ADR-006.)

### 6. CSRF Posture Preserved

Spring Security 7 keeps CSRF protection on by default. `/api/v1/gmail/**` and the OpenAPI/Swagger paths remain CSRF-exempt through the existing `csrf(csrf -> csrf.ignoringRequestMatchers(...))` configuration. That configuration already produced the correct posture under SS7, so no change to the security rules was required.

Verified by the authenticated-POST integration tests (BatchTrash, BatchModifyLabels, SendMessage): Bearer clients get no new 403. The consumer (buzonero) is unaffected.

### 7. Spring Security 7 Relative-Redirect Behavior Change

This is a real deviation from 3.4.0, recorded explicitly although it is benign. For the browser OAuth2 login flow, Spring Security 7 redirects to the **relative** URL `/oauth2/authorization/google` instead of the absolute `http://localhost/oauth2/authorization/google`.

Test assertions were updated from `redirectedUrlPattern("**/oauth2/authorization/google")` to `redirectedUrl("/oauth2/authorization/google")`. The change affects only the browser login redirect, not the Bearer API, and browsers follow relative redirects, so there is no functional impact.

### 8. Dependency Pins Removed

The explicit `logback-classic` / `logback-core` and `slf4j-api` `<version>` pins in `pom.xml` were dropped, along with the Dependabot `<1.6.0` logback cap. The lockstep `logback` Dependabot group was kept.

**Correction to the earlier plan and research:** Spring Boot 4.1.1 does **not** move to logback 1.6.x / slf4j 2.1.x. It still manages logback **1.5.38** and slf4j **2.0.18** (verified with `dependency:list`). Removing the pins is still correct: the versions are now BOM-managed, and logback 1.5.38 retains the CVE-2024-12798 / CVE-2024-12801 fixes the original pin existed for. Net effect is that slf4j effectively shifted from 2.0.20 to 2.0.18.

### 9. Test-Infrastructure Module Split

In Boot 4, `spring-boot-starter-test` no longer transitively provides the web test slices. TEST-scoped dependencies added:

- `spring-boot-starter-webmvc-test`
- `spring-boot-resttestclient`
- `spring-boot-restclient`
- `spring-boot-starter-security-test`

Package moves:

- `@WebMvcTest` / `@AutoConfigureMockMvc` now live in `org.springframework.boot.webmvc.test.autoconfigure`.
- `TestRestTemplate` now lives in `org.springframework.boot.resttestclient`, and `@AutoConfigureTestRestTemplate` is required because the bean is no longer auto-configured.

Notes:

- `spring-boot-starter-security-test` was required for `@WebMvcTest` contexts to load `HttpSecurity`. Without it the run produced about 73 errors / 233 failures.
- `PostmanAuthenticationTest` gained `@AutoConfigureTestRestTemplate` and `spring.http.clients.redirects=dont-follow`, because Boot 4's `TestRestTemplate` follows redirects by default.
- One Spring 7 ambiguous `ResponseEntity` constructor call in `GoogleTokenValidatorTest` was fixed.

### 10. Validation

Full `./mvnw clean verify` is green on Java 17:

- 1,741 tests, 0 failures
- JaCoCo instruction coverage 80.17% and branch coverage 70.36% (gates 0.75 / 0.65)
- Spotless clean
- No `src/main` behavior change beyond the `SecurityConfig` timeout bean
- No tests deleted or disabled, and no `@MockBean` / `@SpyBean` introduced

## Consequences

### Positive

- Supported platform baseline (Spring Framework 7, Spring Security 7, Jakarta EE 11) with the Bearer API contract unchanged.
- Dependency versions for logback and slf4j are BOM-managed, removing manual pins and the Dependabot cap.
- Security posture (CSRF exemption scope, 5 s / 10 s validation timeouts) verified unchanged by tests.

### Negative / Known Limitations

- **Two Jackson generations on the classpath.** Jackson 3 (Spring MVC) and Jackson 2 (Google client, via `spring-boot-jackson2`) coexist. This is an ongoing maintenance cost until the Google client supports Jackson 3.
- **Browser login redirect is now relative** (decision 7). Benign, but any tooling asserting an absolute URL will need the same adjustment.
- **Test setup is more verbose.** Four extra test-scoped dependencies and explicit `@AutoConfigureTestRestTemplate` are now required.
- **Deprecation-for-removal warnings remain** (tech debt, not addressed here): `EnvironmentPostProcessor` in `EnvironmentConfig`, and a deprecated API in `RateLimitInterceptor`.
- **Future JDK floor risk** if JPA/PostgreSQL, jOOQ 3.20+ or Testcontainers are reintroduced (decision 2).

### Follow-ups

- WI-009: Java 25 + virtual threads, delivered as a container.
- Address the two deprecation-for-removal warnings listed above.

## Alternatives Considered

### Alternative 1: Global `csrf.disable()`

**Rejected because:** it would drop CSRF protection from the browser (OAuth2 login) surface as well. The existing path-scoped `ignoringRequestMatchers` already yields the correct posture.

### Alternative 2: Force the Gmail Client to Jackson 3

**Rejected because:** it is a large change with no benefit, given that the `spring-boot-jackson2` compatibility module lets the Google client keep Jackson 2.

### Alternative 3: Migrate `RestTemplate` to `RestClient`

**Out of scope.** Only the removed `setConnectTimeout(int)` was migrated, preserving behavior. A move to `RestClient` can be evaluated separately.

## Related ADRs

- **ADR-001**: Foundation and `TokenProvider` architecture; unchanged by this upgrade.
- **ADR-002**: OAuth2 security context decoupling; the SS7 CSRF and redirect checks above confirm its behavior is preserved.
- **ADR-006**: Shared pooled `ApacheHttpTransport` (Apache HttpClient 4.x) for Gmail calls; distinct from the HttpClient 5 `RestTemplate` factory adjusted in decision 5.

## Files Involved

- `pom.xml`: parent 4.1.1, project version 0.9.0, `spring-boot-jackson2`, springdoc 3.1.1, test-scoped Boot 4 test modules, removed logback/slf4j pins
- `.github/dependabot.yml`: removed the logback `<1.6.0` cap, kept the lockstep `logback` group
- `src/main/java/com/aucontraire/gmailbuddy/config/SecurityConfig.java`: RestTemplate connect/read timeout migration; CSRF configuration (unchanged)
- `src/test/java/com/aucontraire/gmailbuddy/config/SecurityConfigTest.java`: relative-redirect assertion
- `src/test/java/com/aucontraire/gmailbuddy/integration/PostmanAuthenticationTest.java`: `@AutoConfigureTestRestTemplate`, `spring.http.clients.redirects=dont-follow`
- `src/test/java/com/aucontraire/gmailbuddy/service/GoogleTokenValidatorTest.java`: ambiguous `ResponseEntity` constructor fix
- Controller and integration tests under `src/test/java/com/aucontraire/gmailbuddy/`: updated imports for relocated `@WebMvcTest` / `@AutoConfigureMockMvc` / `TestRestTemplate`

---

**Date Created:** 2026-10-07
**Implementation Status:** Complete. `./mvnw clean verify` green on Java 17 (1,741 tests, 0 failures; JaCoCo 80.17% instruction / 70.36% branch)
**Next Review:** After WI-009 (Java 25 + virtual threads)
