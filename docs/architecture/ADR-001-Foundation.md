# ADR-001: Foundation Architecture Improvements

**Status:** In Progress  
**Date:** 2025-07-12  
**Sprint:** Sprint 2 (FB-004)

## Context

Gmail Buddy is undergoing foundation improvements to enhance maintainability, testability, and architectural cleanliness. This ADR documents the architectural decisions made during the foundation improvement phase.

## Current Implementation Status

### Completed Foundation Items (Sprint 1)
- **FB-001: Standardize Response DTOs**  COMPLETED
- **FB-002: Implement Global Exception Handling**  COMPLETED  
- **FB-003: Add Input Validation Framework**  COMPLETED

### Current Work (Sprint 2)
- **FB-004: Decouple Security Context** = IN PROGRESS

## Architectural Decisions

### 1. Security Context Decoupling (FB-004)

**Problem:** Direct dependency on `SecurityContextHolder` throughout the repository layer creates tight coupling to Spring Security infrastructure, making testing difficult and violating dependency inversion principles.

**Decision:** Implement a `TokenProvider` abstraction to decouple OAuth2 token management from Spring Security.

**Implementation:**

#### Created Components:

1. **TokenProvider Interface** (`src/main/java/com/aucontraire/gmailbuddy/service/TokenProvider.java`)
   ```java
   public interface TokenProvider {
       String getAccessToken() throws AuthenticationException;
       String getAccessToken(String userId) throws AuthenticationException;
       boolean isTokenValid();
       boolean isTokenValid(String userId);
       void refreshTokenIfNeeded() throws AuthenticationException;
       void refreshTokenIfNeeded(String userId) throws AuthenticationException;
       String getCurrentPrincipalName() throws AuthenticationException;
   }
   ```

2. **OAuth2TokenProvider Implementation** (`src/main/java/com/aucontraire/gmailbuddy/service/OAuth2TokenProvider.java`)
   - Centralizes all OAuth2 token logic
   - Handles token validation and expiration checks
   - Provides clean abstraction over Spring Security OAuth2

3. **GmailRepositoryImpl Refactoring** (`src/main/java/com/aucontraire/gmailbuddy/repository/GmailRepositoryImpl.java`)
   - Replaced direct `SecurityContextHolder` calls with `TokenProvider` injection
   - Updated constructor to use dependency injection
   - Modified `getGmailService()` method to use token provider abstraction

#### Testing Strategy:

1. **Unit Tests:**
   - `OAuth2TokenProviderTest.java`: Comprehensive unit tests with 15+ test methods
   - `GmailRepositoryImplTest.java`: Repository tests with mocked TokenProvider
   - Uses Mockito with strict mode for clean test practices

2. **Integration Testing:**
   - `TestTokenProvider.java`: Test implementation for integration scenarios
   - `TestTokenProviderConfiguration.java`: Spring test configuration
   - Updated `GmailControllerTest.java` with token provider integration

#### Key Benefits:
- **Testability:** Easy mocking and testing without Spring Security context
- **Separation of Concerns:** Repository layer no longer depends on security infrastructure
- **Dependency Inversion:** High-level modules depend on abstractions, not concretions
- **Flexibility:** Easy to swap token providers for different authentication mechanisms

### 2. Testing Standards

**Decision:** Implement comprehensive testing strategy with both unit and integration tests.

**Standards:**
- All new components must have unit tests with >80% coverage
- Integration tests for critical paths
- Mockito strict mode to prevent unnecessary stubbing
- Clean test methods that only mock what they actually use

**Test Fixes Applied:**
- Fixed compilation errors in test methods
- Resolved Mockito void method stubbing issues
- Eliminated unnecessary stubbing warnings by using precise mocking

## Current Status

### FB-004 Progress:
-  TokenProvider interface created
-  OAuth2TokenProvider implementation completed
-  GmailRepositoryImpl refactored
-  Unit tests created for all components
-  Integration tests updated
- = **CURRENT:** Test fixes in progress (Mockito stubbing issues)

### Pending Test Issues:
Last test run showed 12 errors related to Mockito unnecessary stubbing warnings in `OAuth2TokenProviderTest.java`. These were addressed by:
1. Removing helper methods that created blanket mock setups
2. Implementing precise mocking in each test method
3. Only mocking what each specific test actually uses

### Next Steps:
1. Verify all tests pass (120 tests, 0 errors expected)
2. Complete FB-004 verification
3. Proceed to FB-005: Repository Layer Standardization

## Technical Debt Addressed

1. **Tight Coupling:** Removed direct SecurityContextHolder dependencies from repository layer
2. **Testing Difficulty:** Created mockable abstractions for previously unmockable Spring Security components
3. **Architectural Violations:** Implemented proper dependency injection and inversion of control

## Branch Information

- **Current Branch:** `feature/decouple-security-context`
- **Base Branch:** `master`
- **PR Status:** Not yet created (waiting for test completion)

## Files Modified/Created

### New Files:
- `src/main/java/com/aucontraire/gmailbuddy/service/TokenProvider.java`
- `src/main/java/com/aucontraire/gmailbuddy/service/OAuth2TokenProvider.java`
- `src/test/java/com/aucontraire/gmailbuddy/service/OAuth2TokenProviderTest.java`
- `src/test/java/com/aucontraire/gmailbuddy/repository/GmailRepositoryImplTest.java`
- `src/test/java/com/aucontraire/gmailbuddy/service/TestTokenProvider.java`
- `src/test/java/com/aucontraire/gmailbuddy/config/TestTokenProviderConfiguration.java`

### Modified Files:
- `src/main/java/com/aucontraire/gmailbuddy/repository/GmailRepositoryImpl.java` (constructor and getGmailService method)
- `src/test/java/com/aucontraire/gmailbuddy/controller/GmailControllerTest.java` (added test configuration import)

## Impact Assessment

### Positive Impacts:
- **Improved Testability:** Repository layer can now be tested in isolation
- **Better Architecture:** Clean separation of concerns and dependency inversion
- **Maintainability:** Easier to modify authentication logic without affecting repository
- **Future-Proofing:** Easy to add new authentication mechanisms

### Risk Mitigation:
- Comprehensive test suite ensures no regression in functionality
- Backward compatibility maintained - no public API changes
- Gradual refactoring approach minimizes risk

## Lessons Learned

1. **Mockito Strict Mode:** Requires precise mocking - avoid blanket helper methods that mock unused components
2. **Test Organization:** Each test should only mock what it specifically needs
3. **Dependency Injection:** Proper DI makes components much more testable and maintainable

## References

- PROJECT_PLAN.md: Overall project roadmap and sprint planning
- docs/github/FB-004_DECOUPLE_SECURITY_CONTEXT_ISSUE.md: Detailed issue documentation
- Spring Security OAuth2 Client documentation
- Mockito testing best practices