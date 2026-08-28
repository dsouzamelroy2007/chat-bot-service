package com.mel.cb.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyAuthFilterTest {

  @Test
  void allowsAnyRequestWhenNoApiKeyConfigured() throws Exception {
    ApiKeyAuthFilter filter = new ApiKeyAuthFilter(new SecurityProperties());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void rejectsMissingHeaderWhenApiKeyConfigured() throws Exception {
    ApiKeyAuthFilter filter = new ApiKeyAuthFilter(withApiKey("secret"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void rejectsWrongHeaderWhenApiKeyConfigured() throws Exception {
    ApiKeyAuthFilter filter = new ApiKeyAuthFilter(withApiKey("secret"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    request.addHeader("X-API-Key", "wrong");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void allowsMatchingHeaderWhenApiKeyConfigured() throws Exception {
    ApiKeyAuthFilter filter = new ApiKeyAuthFilter(withApiKey("secret"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    request.addHeader("X-API-Key", "secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  private static SecurityProperties withApiKey(String apiKey) {
    SecurityProperties properties = new SecurityProperties();
    properties.setApiKey(apiKey);
    return properties;
  }

}
