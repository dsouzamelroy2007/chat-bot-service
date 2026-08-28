package com.mel.cb.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

  private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

  @Test
  void setsBaselineHeadersOnEveryResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bot/");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.getHeader("Cache-Control")).isNull();
  }

  @Test
  void addsNoStoreOnlyForChatPaths() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

}
