package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.dto.CreateLabelRequest;
import com.aucontraire.gmailbuddy.dto.response.LabelSummary;
import com.aucontraire.gmailbuddy.exception.LabelAlreadyExistsException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice contract tests for {@code POST /api/v1/gmail/labels}
 * (feature 005 US3, T022).
 *
 * <p>{@link GmailService} is mocked so no Gmail API calls are made — these are
 * request/response contract tests only. Uses the full {@code @SpringBootTest}
 * security context (mirroring {@code BatchTrashControllerTest} /
 * {@code GmailControllerTest}) so the real {@code SecurityConfig} filter chain,
 * bean validation, and {@code GlobalExceptionHandler} are exercised, rather than
 * re-stubbing them.</p>
 *
 * <p>Covers: success (201, not 200), duplicate-name conflict (409, name never
 * echoed back per FR-015), blank/missing/control-character name (400), the
 * deliberate allowance of {@code /} in nested-label names (FR-011 — no
 * {@code @SafeFilename} reuse), {@code messageListVisibility} enum validation,
 * and an unauthenticated request.</p>
 */
@SpringBootTest(classes = GmailBuddyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
@DisplayName("POST /api/v1/gmail/labels — contract (T022)")
class CreateLabelControllerTest {

    private static final String CREATE_LABEL_ENDPOINT = "/api/v1/gmail/labels";
    private static final String USER_ID = "me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    void authenticateAsTestUser() {
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", null, null);
        OAuth2User principal = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                Collections.singletonMap("name", "testuser"),
                "name"
        );
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // -------------------------------------------------------------------------
    // (a) Valid name -> 201 CREATED (not 200) with the LabelSummary body
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validName_returns201CreatedWithLabelSummaryBody")
    void createLabel_validName_returns201CreatedWithLabelSummaryBody() throws Exception {
        // Arrange
        CreateLabelRequest request = new CreateLabelRequest("pending-purge", null, null);
        LabelSummary serviceResult = new LabelSummary("Label_123", "pending-purge", "user", null, null);
        when(gmailService.createLabel(eq(USER_ID), eq("pending-purge"), isNull(), isNull()))
                .thenReturn(serviceResult);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert: isCreated() asserts exactly HTTP 201, distinguishing it from
        // the 200 that a plain ResponseEntity.ok(...) would produce.
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("Label_123"))
                .andExpect(jsonPath("$.name").value("pending-purge"))
                .andExpect(jsonPath("$.type").value("user"));
    }

    // -------------------------------------------------------------------------
    // (a.1) X-Gmail-Quota-Used header (T031) — LABEL_CREATE_QUOTA (1 unit)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validName_setsGmailQuotaUsedHeaderToOne")
    void createLabel_validName_setsGmailQuotaUsedHeaderToOne() throws Exception {
        // Arrange: GmailController.LABEL_CREATE_QUOTA mirrors GmailQuotaEstimator's
        // 1-unit cost for label endpoints (FR-013).
        CreateLabelRequest request = new CreateLabelRequest("quota-check-label", null, null);
        LabelSummary serviceResult = new LabelSummary("Label_999", "quota-check-label", "user", null, null);
        when(gmailService.createLabel(eq(USER_ID), eq("quota-check-label"), isNull(), isNull()))
                .thenReturn(serviceResult);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Gmail-Quota-Used", "1"));
    }

    // -------------------------------------------------------------------------
    // (b) Duplicate name -> 409 /problems/resource-conflict, name never echoed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_duplicateName_returns409ResourceConflictWithoutEchoingName")
    void createLabel_duplicateName_returns409ResourceConflictWithoutEchoingName() throws Exception {
        // Arrange: the exception message is deliberately generic (FR-015) — the
        // requested name must never reach the response body.
        String requestedName = "Recruiters-Do-Not-Leak-Me";
        CreateLabelRequest request = new CreateLabelRequest(requestedName, null, null);
        when(gmailService.createLabel(eq(USER_ID), eq(requestedName), isNull(), isNull()))
                .thenThrow(new LabelAlreadyExistsException("A label with the requested name already exists"));
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/resource-conflict"))
                .andExpect(content().string(not(containsString(requestedName))));
    }

    // -------------------------------------------------------------------------
    // (c) Blank name -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_blankName_returns400WithValidationErrorProblemType")
    void createLabel_blankName_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: @NotBlank on CreateLabelRequest.name rejects before the controller
        // method body runs — no GmailService stubbing needed.
        String requestBody = "{\"name\": \"\"}";

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (d) Missing name field -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_missingNameField_returns400WithValidationErrorProblemType")
    void createLabel_missingNameField_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: no "name" key at all -> deserializes to null -> @NotBlank rejects.
        String requestBody = "{}";

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (e) Control character in name -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_nameWithEmbeddedControlCharacter_returns400WithHeaderInjectionProblemType")
    void createLabel_nameWithEmbeddedControlCharacter_returns400WithHeaderInjectionProblemType()
            throws Exception {
        // Arrange: embedded LF (\n) is rejected by both @NoHeaderInjection and the
        // \p{Cntrl}-based @Pattern on CreateLabelRequest.name. Per
        // GlobalExceptionHandler#handleMethodArgumentNotValidException, when any failing
        // FieldError carries a @NoHeaderInjection constraint code the response uses the
        // header-injection-detected problem type (not the generic validation-error one)
        // so security-relevant attempts can be triaged separately (FR-015, FR-018, SC-005).
        String requestBody = "{\"name\": \"Bad\\nLabel\"}";

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/header-injection-detected"));
    }

    // -------------------------------------------------------------------------
    // (e.1) Name exceeding 225 characters -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_nameExceeds225Characters_returns400WithValidationErrorProblemType")
    void createLabel_nameExceeds225Characters_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: @Size(max = 225) on CreateLabelRequest.name rejects before the
        // controller method body runs — no GmailService stubbing needed.
        String oversizedName = "a".repeat(226);
        String requestBody = objectMapper.writeValueAsString(new CreateLabelRequest(oversizedName, null, null));

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (f) Name containing '/' (nested label) -> allowed, NOT rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_nestedLabelNameWithSlash_returns201CreatedNotRejected")
    void createLabel_nestedLabelNameWithSlash_returns201CreatedNotRejected() throws Exception {
        // Arrange: "Parent/Child" is Gmail's legitimate nested-label naming convention.
        // CreateLabelRequest deliberately does not reuse @SafeFilename (which rejects
        // '/' for path-traversal reasons) so this must pass validation.
        CreateLabelRequest request = new CreateLabelRequest("Parent/Child", null, null);
        LabelSummary serviceResult = new LabelSummary("Label_456", "Parent/Child", "user", null, null);
        when(gmailService.createLabel(eq(USER_ID), eq("Parent/Child"), isNull(), isNull()))
                .thenReturn(serviceResult);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Parent/Child"));
    }

    // -------------------------------------------------------------------------
    // (g) Invalid messageListVisibility -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_invalidMessageListVisibility_returns400WithValidationErrorProblemType")
    void createLabel_invalidMessageListVisibility_returns400WithValidationErrorProblemType()
            throws Exception {
        // Arrange: "visible" is not one of Gmail's allowed values ("show" | "hide").
        String requestBody = "{\"name\": \"Test Label\", \"messageListVisibility\": \"visible\"}";

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (h) Valid messageListVisibility ("show") -> passes validation, 201
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validMessageListVisibilityShow_returns201Created")
    void createLabel_validMessageListVisibilityShow_returns201Created() throws Exception {
        // Arrange
        CreateLabelRequest request = new CreateLabelRequest("Test Label", "show", null);
        LabelSummary serviceResult = new LabelSummary("Label_789", "Test Label", "user", "show", null);
        when(gmailService.createLabel(eq(USER_ID), eq("Test Label"), eq("show"), isNull()))
                .thenReturn(serviceResult);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageListVisibility").value("show"));
    }

    // -------------------------------------------------------------------------
    // (h.1) Invalid labelListVisibility -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_invalidLabelListVisibility_returns400WithValidationErrorProblemType")
    void createLabel_invalidLabelListVisibility_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: "labelVisible" is not one of Gmail's allowed values
        // ("labelShow" | "labelShowIfUnread" | "labelHide").
        String requestBody = "{\"name\": \"Test Label\", \"labelListVisibility\": \"labelVisible\"}";

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (h.2) Valid labelListVisibility ("labelShow") -> passes validation, 201
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validLabelListVisibilityLabelShow_returns201Created")
    void createLabel_validLabelListVisibilityLabelShow_returns201Created() throws Exception {
        // Arrange
        CreateLabelRequest request = new CreateLabelRequest("Test Label", null, "labelShow");
        LabelSummary serviceResult = new LabelSummary("Label_321", "Test Label", "user", null, "labelShow");
        when(gmailService.createLabel(eq(USER_ID), eq("Test Label"), isNull(), eq("labelShow")))
                .thenReturn(serviceResult);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labelListVisibility").value("labelShow"));
    }

    // -------------------------------------------------------------------------
    // (i) Unauthenticated -> redirected to the OAuth2 login entry point
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_unauthenticated_redirectsToOAuth2LoginEntryPoint")
    void createLabel_unauthenticated_redirectsToOAuth2LoginEntryPoint() throws Exception {
        // Arrange: SecurityConfig registers LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")
        // as the authenticationEntryPoint, so unauthenticated requests receive a 302 redirect
        // rather than a raw 401 — the same behavior BatchTrashControllerTest / GmailControllerTest
        // document for every other authenticated endpoint on this controller.
        SecurityContextHolder.clearContext();
        CreateLabelRequest request = new CreateLabelRequest("Test Label", null, null);
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post(CREATE_LABEL_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isFound());
    }
}
