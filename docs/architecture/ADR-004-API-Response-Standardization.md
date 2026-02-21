# ADR-004: API Response Standardization

**Status:** Implemented
**Date:** 2026-02-20
**Sprint:** SPIKE-001 Implementation
**Relates to:** ADR-001 Foundation Architecture Improvements, ADR-002 OAuth2 Security Context Decoupling

## Context

Following the completion of SPIKE-001 research, Gmail Buddy's API responses exhibited significant inconsistencies that impaired usability, maintainability, and developer experience. Manual testing revealed critical issues:

### Problems Identified

1. **Inconsistent Error Responses**: Different endpoints returned errors in varying formats without a unified structure
2. **Missing API Documentation**: No interactive documentation for developers to explore and test endpoints
3. **Lack of Standardization**: No RFC compliance for error handling or consistent patterns across success responses
4. **Poor Developer Experience**: Clients had no self-documenting API or consistent error handling patterns

### Business Impact

- **Developer Productivity**: Integration teams struggled with inconsistent API patterns
- **Debugging Complexity**: No correlation IDs or standardized error information made troubleshooting difficult
- **API Discoverability**: Lack of interactive documentation slowed onboarding and testing
- **Maintenance Burden**: Each endpoint required custom error handling logic

### SPIKE-001 Research

Comprehensive research into REST API best practices (SPIKE-001-API-Response-Standardization.md, 140+ pages) evaluated multiple industry standards:

- **RFC 7807 (Problem Details for HTTP APIs)**: Standardized error response format
- **OpenAPI/Swagger**: Interactive API documentation and specification
- **Microsoft REST API Guidelines**: Enterprise-grade API design patterns
- **JSend Specification**: Simple JSON response format
- **Google JSON Style Guide**: JSON structure conventions

Research concluded that implementing RFC 7807 for errors combined with OpenAPI documentation would provide the best balance of standards compliance and developer experience.

## Decision

Implement a three-part API standardization approach:

### 1. RFC 7807 Compliant Error Responses

Adopt RFC 7807 (Problem Details for HTTP APIs) as the standard for all error responses across the application.

**Rationale:**
- Industry standard with broad tooling support
- Machine-readable error details with consistent structure
- Extensible for application-specific needs
- Proper HTTP semantics (Content-Type: application/problem+json)
- Supports correlation IDs and retry guidance

**Implementation:**
- `ProblemDetail` class providing RFC 7807 compliant structure
- Standard fields: `type`, `title`, `status`, `detail`, `instance`
- Gmail Buddy extensions: `requestId`, `timestamp`, `retryable`, `category`
- Builder pattern for clean construction
- Validation of required RFC 7807 fields

### 2. OpenAPI/Swagger Documentation

Implement comprehensive API documentation using springdoc-openapi 2.8.5 (Spring Boot 3.4 compatible).

**Rationale:**
- Interactive API exploration via Swagger UI
- Auto-generated OpenAPI specification (v3)
- Supports OAuth2 and Bearer token authentication schemes
- Zero boilerplate for basic documentation
- Industry-standard specification format

**Implementation:**
- `OpenApiConfig` configuration class
- Swagger UI at `/swagger-ui/index.html`
- OpenAPI spec at `/v3/api-docs`
- OAuth2 and Bearer token security schemes
- Rate limiting and quota documentation

### 3. GlobalExceptionHandler Enhancement

Centralize all exception handling with RFC 7807 compliance and proper basePackages configuration.

**Rationale:**
- Single source of truth for error handling
- Prevents catching OpenAPI framework exceptions
- Consistent correlation ID generation via MDC
- Proper HTTP status code mapping
- Comprehensive logging with request tracing

**Implementation:**
- `@RestControllerAdvice(basePackages = "com.aucontraire.gmailbuddy.controller")`
- Handlers for all custom exceptions (ResourceNotFoundException, GmailApiException, etc.)
- MDC integration for correlation IDs
- Content-Type: application/problem+json headers
- Retry-After header support for rate limiting

## Implementation Details

### RFC 7807 ProblemDetail Structure

**File:** `src/main/java/com/aucontraire/gmailbuddy/dto/error/ProblemDetail.java`

```json
{
  "type": "https://api.gmailbuddy.com/errors/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Message with ID 'abc123' not found",
  "instance": "/api/v1/gmail/messages/abc123",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-02-20T10:30:00Z",
  "retryable": false,
  "category": "CLIENT_ERROR"
}
```

**Key Features:**
- Builder pattern with validation
- Immutable after construction
- JSON serialization with @JsonProperty annotations
- Optional extensions map for additional context
- Null-safe with @JsonInclude(JsonInclude.Include.NON_NULL)

### OpenAPI Configuration

**File:** `src/main/java/com/aucontraire/gmailbuddy/config/OpenApiConfig.java`

**Configuration:**
- API Info: Title, version, description, contact, license
- Server: Local development server (localhost:8020)
- Security Schemes:
  - `bearerAuth`: HTTP Bearer token authentication
  - `oauth2`: OAuth2 authorization code flow with Google
- Gmail API scopes: email, profile, gmail.readonly, gmail.modify
- Rate limiting header documentation

**Access Points:**
- Swagger UI: `http://localhost:8020/swagger-ui/index.html`
- OpenAPI Spec (JSON): `http://localhost:8020/v3/api-docs`
- OpenAPI Spec (YAML): `http://localhost:8020/v3/api-docs.yaml`

### GlobalExceptionHandler Updates

**File:** `src/main/java/com/aucontraire/gmailbuddy/exception/GlobalExceptionHandler.java`

**Critical Configuration:**
```java
@RestControllerAdvice(basePackages = "com.aucontraire.gmailbuddy.controller")
```

This `basePackages` restriction prevents the handler from catching OpenAPI framework exceptions, which would break Swagger UI functionality.

**Exception Mappings:**
- `ResourceNotFoundException` → 404 Not Found
- `GmailApiException` → 502 Bad Gateway (retryable)
- `AuthenticationException` → 401 Unauthorized
- `AccessDeniedException` → 403 Forbidden
- `MethodArgumentNotValidException` → 400 Bad Request (validation errors)
- `ConstraintViolationException` → 400 Bad Request
- `Exception` → 500 Internal Server Error (catch-all)

**MDC Correlation IDs:**
All exception handlers retrieve correlation IDs from MDC (Mapped Diagnostic Context) for consistent request tracing across logs and error responses.

### Controller Annotations

Controllers and DTOs are enhanced with OpenAPI annotations:

**Controller Level:**
- `@Tag(name = "Gmail Messages", description = "Gmail message management operations")`

**Operation Level:**
- `@Operation(summary = "Delete message", description = "Delete a single Gmail message by ID")`
- `@ApiResponse(responseCode = "200", description = "Message deleted successfully")`
- `@ApiResponse(responseCode = "404", description = "Message not found")`
- `@ApiResponse(responseCode = "401", description = "Unauthorized")`

**DTO Level:**
- `@Schema(description = "Filter criteria for searching Gmail messages")`
- Field-level `@Schema` annotations for property documentation

### Maven Dependency

**pom.xml:**
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.5</version>
</dependency>
```

**Version Compatibility:**
- springdoc-openapi 2.8.x required for Spring Boot 3.4.0
- Earlier versions (2.6.x, 2.7.x) incompatible with Spring Boot 3.4
- Provides both OpenAPI spec generation and Swagger UI

## Consequences

### Positive Consequences

1. **Standardized Error Handling**
   - All errors follow RFC 7807 format
   - Machine-readable error details
   - Consistent structure across all endpoints
   - Proper HTTP semantics (status codes, Content-Type headers)

2. **Interactive API Documentation**
   - Developers can explore and test APIs via Swagger UI
   - Auto-generated OpenAPI specification for client generation
   - OAuth2 and Bearer token authentication support in UI
   - Zero documentation drift (generated from code)

3. **Improved Debugging**
   - Correlation IDs in all error responses
   - MDC integration for request tracing through logs
   - Detailed error information with retry guidance
   - Category-based error classification

4. **Better Developer Experience**
   - Self-documenting API reduces onboarding time
   - Consistent error patterns simplify client error handling
   - Clear authentication schemes and security requirements
   - Rate limiting visibility in documentation

5. **Standards Compliance**
   - RFC 7807 compliance enables standard tooling
   - OpenAPI 3.0 specification for broad ecosystem support
   - Industry-standard patterns improve credibility

6. **Maintainability**
   - Centralized exception handling reduces code duplication
   - Annotations keep documentation close to code
   - Builder pattern for ProblemDetail prevents invalid error responses
   - basePackages configuration prevents framework conflicts

### Negative Consequences

1. **Learning Curve**
   - Team members must understand RFC 7807 structure
   - OpenAPI annotation usage requires documentation
   - MDC correlation ID management adds complexity

2. **Response Size**
   - RFC 7807 responses larger than minimal error formats
   - Additional metadata fields increase payload size
   - Acceptable trade-off for improved debugging

3. **Dependency Management**
   - springdoc-openapi version tightly coupled to Spring Boot version
   - Must upgrade springdoc when upgrading Spring Boot
   - Version incompatibilities can break Swagger UI

4. **Configuration Sensitivity**
   - `basePackages` in @RestControllerAdvice critical for OpenAPI
   - Missing this configuration breaks Swagger UI
   - Requires understanding of Spring exception handling order

### Migration Considerations

**No Breaking Changes:**
The implementation was additive with no breaking changes to existing clients:

- Existing success response structures unchanged
- Error responses improved but maintain compatibility
- New documentation endpoints don't affect existing APIs
- OAuth2 authentication mechanisms unchanged

**Testing Requirements:**
- Verify all exception types map to correct ProblemDetail responses
- Test Swagger UI functionality with OAuth2 flow
- Validate correlation IDs propagate through MDC
- Ensure basePackages configuration doesn't break OpenAPI

## Alternatives Considered

### Alternative 1: Minimal Fix (SPIKE-001 Option A)

**Description:** Fix immediate bugs without standardization

**Pros:**
- Quick implementation (1-2 days)
- Low risk, minimal changes
- Addresses immediate inconsistencies

**Cons:**
- Doesn't solve broader consistency problems
- No interactive documentation
- Technical debt accumulates

**Rejection Reason:** Doesn't address root cause; short-term fix only

### Alternative 2: Full Envelope Approach (SPIKE-001 Option B)

**Description:** Wrap all responses in envelope structure

```json
{
  "status": "success",
  "data": {...},
  "metadata": {...}
}
```

**Pros:**
- Complete consistency across all responses
- Easy metadata addition
- Simple pattern to understand

**Cons:**
- Dilutes HTTP semantics (always 200 OK)
- Breaking change for existing clients
- Over-engineering for current needs

**Rejection Reason:** Loses proper HTTP status code usage; unnecessary complexity

### Alternative 3: JSend Specification

**Description:** Adopt JSend standard for all responses

**Pros:**
- Simple, well-documented standard
- Clear success/failure distinction
- Minimal learning curve

**Cons:**
- Always returns 200 OK (loses HTTP semantics)
- No support for partial success
- Limited error detail structure

**Rejection Reason:** Doesn't leverage HTTP properly; less detailed than RFC 7807

### Alternative 4: Custom Error Format

**Description:** Design Gmail Buddy-specific error format

**Pros:**
- Complete control over structure
- Optimized for exact use cases
- No external standard constraints

**Cons:**
- No tooling support
- Requires client-specific parsing
- Reinvents existing standards

**Rejection Reason:** RFC 7807 provides same benefits with industry support

## Success Metrics

### Quantitative Metrics

- **100% Exception Coverage**: All custom exceptions mapped to ProblemDetail responses
- **Zero OpenAPI Conflicts**: Swagger UI fully functional with basePackages configuration
- **All Endpoints Documented**: Complete OpenAPI spec for all public APIs
- **Correlation ID Coverage**: 100% of error responses include correlation IDs

### Qualitative Metrics

- **Developer Feedback**: Positive feedback on Swagger UI usability
- **Debugging Efficiency**: Reduced time to diagnose issues using correlation IDs
- **Onboarding Speed**: New developers can explore API without extensive documentation
- **Standards Compliance**: External audits confirm RFC 7807 compliance

## Implementation Timeline

**Phase 1: Foundation (Completed)**
- Created ProblemDetail class with RFC 7807 structure
- Implemented builder pattern with validation
- Added common error types (ProblemTypes constants)

**Phase 2: Exception Handling (Completed)**
- Updated GlobalExceptionHandler with ProblemDetail responses
- Added basePackages configuration for OpenAPI compatibility
- Implemented MDC correlation ID integration
- Mapped all custom exceptions to appropriate ProblemDetail types

**Phase 3: OpenAPI Documentation (Completed)**
- Added springdoc-openapi-starter-webmvc-ui dependency (2.8.5)
- Created OpenApiConfig with OAuth2 and Bearer auth schemes
- Configured Swagger UI and OpenAPI spec endpoints
- Documented authentication flows and rate limiting

**Phase 4: Controller Annotations (In Progress)**
- Adding @Operation and @ApiResponse annotations to controllers
- Enhancing DTOs with @Schema annotations
- Documenting request/response examples
- Validating Swagger UI completeness

## Related Documentation

### Architecture Decision Records
- **ADR-001**: Foundation Architecture Improvements - Established DTO patterns
- **ADR-002**: OAuth2 Security Context Decoupling - Authentication architecture
- **ADR-003**: Performance Crisis Recovery - Batch operations and adaptive algorithms

### SPIKE Documents
- **SPIKE-001-EXECUTIVE-SUMMARY.md**: Decision rationale and option comparison
- **SPIKE-001-API-Response-Standardization.md**: Comprehensive 140-page analysis
- **SPIKE-001-Error-Response-Analysis.md**: Detailed error handling research
- **SPIKE-001-Gmail-API-Integration-Analysis.md**: Gmail API integration patterns

### Implementation Plans
- **IMPLEMENTATION-PLAN-MODIFIED-OPTION-C.md**: Detailed implementation phases and code examples

### Code References
- `src/main/java/com/aucontraire/gmailbuddy/dto/error/ProblemDetail.java`: RFC 7807 implementation
- `src/main/java/com/aucontraire/gmailbuddy/config/OpenApiConfig.java`: OpenAPI configuration
- `src/main/java/com/aucontraire/gmailbuddy/exception/GlobalExceptionHandler.java`: Centralized exception handling
- `src/main/java/com/aucontraire/gmailbuddy/constants/ProblemTypes.java`: Error type URIs

## Future Considerations

### Potential Enhancements

1. **OpenAPI Client Generation**
   - Generate TypeScript/Java clients from OpenAPI spec
   - Distribute client libraries for common languages
   - Automate client generation in CI/CD pipeline

2. **Enhanced Error Recovery**
   - Add suggested actions to ProblemDetail extensions
   - Implement error recovery workflows
   - Provide fix-it links in error responses

3. **API Versioning**
   - Support multiple API versions via OpenAPI
   - Version-specific error types
   - Deprecation warnings in responses

4. **Monitoring Integration**
   - Export OpenAPI spec to API monitoring tools
   - Track error categories and frequencies
   - Alert on error rate thresholds

5. **Response Time Tracking**
   - Add X-Response-Time headers to success responses
   - Include timing metadata in responses
   - Performance monitoring via response headers

### Lessons Learned

1. **basePackages Configuration Critical**
   - @RestControllerAdvice without basePackages catches OpenAPI exceptions
   - Breaks Swagger UI with 500 errors
   - Always restrict exception handlers to application controllers

2. **Version Compatibility Matters**
   - springdoc-openapi 2.8+ required for Spring Boot 3.4
   - Incompatible versions cause runtime failures
   - Check compatibility before upgrading Spring Boot

3. **RFC 7807 Builder Pattern**
   - Builder validation prevents invalid error responses
   - Required fields enforced at compile time
   - Immutable ProblemDetail ensures thread safety

4. **MDC for Correlation IDs**
   - Consistent request tracing across logs and responses
   - Filter-based MDC setup ensures coverage
   - Clean MDC after request completion prevents leaks

5. **Documentation Close to Code**
   - OpenAPI annotations keep docs in sync with implementation
   - Reduces documentation drift
   - Easier to maintain than separate documentation

## References

### Standards and Specifications
- RFC 7807: Problem Details for HTTP APIs - https://tools.ietf.org/html/rfc7807
- OpenAPI Specification 3.0 - https://spec.openapis.org/oas/v3.0.0
- RFC 5988: Web Linking (Link headers) - https://tools.ietf.org/html/rfc5988

### Libraries and Tools
- springdoc-openapi - https://springdoc.org/
- Swagger UI - https://swagger.io/tools/swagger-ui/
- Spring Boot 3.4 - https://spring.io/projects/spring-boot

### Gmail Buddy Documentation
- CLAUDE.md: Project development guidelines
- PROJECT_PLAN.md: Overall roadmap and sprint planning
- Postman collection: docs/Gmail-Buddy-API.postman_collection.json

---

**Last Updated:** 2026-02-20
**Implementation Status:** Completed (Phases 1-3), In Progress (Phase 4)
**Next Review:** After Phase 4 completion
