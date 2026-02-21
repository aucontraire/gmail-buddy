package com.aucontraire.gmailbuddy.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LinkHeaderBuilder Unit Tests")
class LinkHeaderBuilderTest {

    private static final String BASE_URI = "/api/v1/gmail/messages";
    private static final String SERVER_NAME = "localhost";
    private static final int SERVER_PORT = 8020;
    private static final String SCHEME = "http";
    private static final String BASE_URL = "http://localhost:8020/api/v1/gmail/messages";

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(BASE_URI);
        request.setServerName(SERVER_NAME);
        request.setServerPort(SERVER_PORT);
        request.setScheme(SCHEME);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("addNext() with valid token generates correct link format")
    void addNext_withValidToken_generatesCorrectLinkFormat() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String nextToken = "next-page-token-123";

        // Act
        String result = builder.addNext(nextToken).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .isEqualTo("<" + BASE_URL + "?pageToken=" + nextToken + ">; rel=\"next\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("addNext() with null/empty token doesn't add link")
    void addNext_withNullOrEmptyToken_doesNotAddLink(String token) {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addNext(token).build();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("addPrev() with valid token generates correct link format")
    void addPrev_withValidToken_generatesCorrectLinkFormat() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String prevToken = "prev-page-token-456";

        // Act
        String result = builder.addPrev(prevToken).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .isEqualTo("<" + BASE_URL + "?pageToken=" + prevToken + ">; rel=\"prev\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("addPrev() with null/empty token doesn't add link")
    void addPrev_withNullOrEmptyToken_doesNotAddLink(String token) {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addPrev(token).build();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("addFirst() generates link without pageToken parameter")
    void addFirst_generatesLinkWithoutPageTokenParameter() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addFirst().build();

        // Assert
        assertThat(result)
            .isNotNull()
            .isEqualTo("<" + BASE_URL + ">; rel=\"first\"")
            .doesNotContain("pageToken");
    }

    @Test
    @DisplayName("addLast() with valid token generates correct link format")
    void addLast_withValidToken_generatesCorrectLinkFormat() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String lastToken = "last-page-token-789";

        // Act
        String result = builder.addLast(lastToken).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .isEqualTo("<" + BASE_URL + "?pageToken=" + lastToken + ">; rel=\"last\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("addLast() with null/empty token doesn't add link")
    void addLast_withNullOrEmptyToken_doesNotAddLink(String token) {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addLast(token).build();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("build() returns null when no links added")
    void build_withNoLinksAdded_returnsNull() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.build();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("build() returns single link correctly formatted")
    void build_withSingleLink_returnsCorrectlyFormattedLink() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String token = "single-token";

        // Act
        String result = builder.addNext(token).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .startsWith("<http://")
            .contains(">; rel=\"next\"")
            .contains("pageToken=" + token);
    }

    @Test
    @DisplayName("build() returns multiple links comma-separated")
    void build_withMultipleLinks_returnsCommaSeparatedLinks() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String nextToken = "next-token";
        String prevToken = "prev-token";

        // Act
        String result = builder
            .addPrev(prevToken)
            .addNext(nextToken)
            .build();

        // Assert
        assertThat(result)
            .isNotNull()
            .contains(", ")
            .contains("<" + BASE_URL + "?pageToken=" + prevToken + ">; rel=\"prev\"")
            .contains("<" + BASE_URL + "?pageToken=" + nextToken + ">; rel=\"next\"");
    }

    @Test
    @DisplayName("RFC 5988 format compliance: <url>; rel=\"type\"")
    void linkFormat_compliesWithRFC5988() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String token = "test-token";

        // Act
        String result = builder.addNext(token).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .matches("^<[^>]+>; rel=\"[^\"]+\"$")  // Matches RFC 5988 format
            .startsWith("<")
            .contains(">; rel=\"")
            .endsWith("\"");
    }

    @Test
    @DisplayName("Chaining multiple link methods works correctly")
    void chainingMultipleLinkMethods_worksCorrectly() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String nextToken = "next-123";
        String prevToken = "prev-456";
        String lastToken = "last-789";

        // Act
        String result = builder
            .addFirst()
            .addPrev(prevToken)
            .addNext(nextToken)
            .addLast(lastToken)
            .build();

        // Assert
        assertThat(result)
            .isNotNull()
            .contains("<" + BASE_URL + ">; rel=\"first\"")
            .contains("<" + BASE_URL + "?pageToken=" + prevToken + ">; rel=\"prev\"")
            .contains("<" + BASE_URL + "?pageToken=" + nextToken + ">; rel=\"next\"")
            .contains("<" + BASE_URL + "?pageToken=" + lastToken + ">; rel=\"last\"");

        // Verify comma separation between all links
        String[] links = result.split(", ");
        assertThat(links).hasSize(4);
    }

    @Test
    @DisplayName("Chaining returns same builder instance for fluent API")
    void chainingMethods_returnsSameBuilderInstance() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act & Assert
        assertThat(builder.addNext("token")).isSameAs(builder);
        assertThat(builder.addPrev("token")).isSameAs(builder);
        assertThat(builder.addFirst()).isSameAs(builder);
        assertThat(builder.addLast("token")).isSameAs(builder);
    }

    @Test
    @DisplayName("Multiple links maintain insertion order")
    void multipleLinks_maintainInsertionOrder() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder
            .addFirst()
            .addPrev("prev-token")
            .addNext("next-token")
            .addLast("last-token")
            .build();

        // Assert
        String[] links = result.split(", ");
        assertThat(links[0]).contains("rel=\"first\"");
        assertThat(links[1]).contains("rel=\"prev\"");
        assertThat(links[2]).contains("rel=\"next\"");
        assertThat(links[3]).contains("rel=\"last\"");
    }

    @Test
    @DisplayName("Builder handles special characters in tokens")
    void builder_handlesSpecialCharactersInTokens() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String tokenWithSpecialChars = "token-with-special_chars.123";

        // Act
        String result = builder.addNext(tokenWithSpecialChars).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .contains("pageToken=" + tokenWithSpecialChars)
            .matches("^<[^>]+>; rel=\"next\"$");
    }

    @Test
    @DisplayName("Builder with existing query parameters in URI preserves them")
    void builder_withExistingQueryParametersInURI_preservesThem() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(BASE_URI);
        request.setQueryString("maxResults=50");
        request.setServerName(SERVER_NAME);
        request.setServerPort(SERVER_PORT);
        request.setScheme(SCHEME);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        LinkHeaderBuilder builder = new LinkHeaderBuilder();
        String nextToken = "next-token";

        // Act
        String result = builder.addNext(nextToken).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .contains("maxResults=50")
            .contains("pageToken=" + nextToken);
    }

    @Test
    @DisplayName("addFirst() removes pageToken from existing query parameters in URI")
    void addFirst_removesPageTokenFromExistingQueryParametersInURI() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(BASE_URI);
        request.setQueryString("pageToken=current-token&maxResults=50");
        request.setServerName(SERVER_NAME);
        request.setServerPort(SERVER_PORT);
        request.setScheme(SCHEME);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addFirst().build();

        // Assert
        assertThat(result)
            .isNotNull()
            .doesNotContain("pageToken")
            .contains("maxResults=50");
    }

    @Test
    @DisplayName("Whitespace-only token is not treated as empty and adds link with spaces")
    void addNext_withWhitespaceOnlyToken_addsLinkWithSpaces() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addNext("   ").build();

        // Assert - whitespace is not empty per String.isEmpty(), so it will be added
        // ServletUriComponentsBuilder does not URL-encode spaces in the output
        assertThat(result)
            .isNotNull()
            .contains("pageToken=   ");
    }

    @Test
    @DisplayName("Mixed valid and null tokens only add valid links")
    void mixedValidAndNullTokens_onlyAddValidLinks() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder
            .addNext("next-token")
            .addPrev(null)
            .addLast("")
            .build();

        // Assert
        String[] links = result.split(", ");
        assertThat(links).hasSize(1);
        assertThat(links[0]).contains("rel=\"next\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"token123", "abc-def-ghi", "token_with_underscore", "123456789"})
    @DisplayName("Various valid token formats work correctly")
    void variousValidTokenFormats_workCorrectly(String token) {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder.addNext(token).build();

        // Assert
        assertThat(result)
            .isNotNull()
            .contains("pageToken=" + token)
            .matches("^<[^>]+>; rel=\"next\"$");
    }

    @Test
    @DisplayName("Link header contains no extra whitespace except after comma")
    void linkHeader_containsNoExtraWhitespace() {
        // Arrange
        LinkHeaderBuilder builder = new LinkHeaderBuilder();

        // Act
        String result = builder
            .addPrev("prev-token")
            .addNext("next-token")
            .build();

        // Assert
        assertThat(result)
            .isNotNull()
            .matches("^<[^>]+>; rel=\"[^\"]+\", <[^>]+>; rel=\"[^\"]+\"$")
            .doesNotContain("  ")  // No double spaces
            .doesNotContain("< ")
            .doesNotContain(" >");
    }
}