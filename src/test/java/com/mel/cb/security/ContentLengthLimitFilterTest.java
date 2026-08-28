package com.mel.cb.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ContentLengthLimitFilterTest {

  private SecurityProperties properties() {
    SecurityProperties properties = new SecurityProperties();
    properties.setMaxRequestBytes(10);
    return properties;
  }

  @Test
  void rejectsBodyOverTheConfiguredLimit() throws Exception {
    ContentLengthLimitFilter filter = new ContentLengthLimitFilter(properties());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    request.setContent(new byte[20]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getContentAsString()).contains("exceeds the maximum allowed size");
  }

  @Test
  void allowsBodyAtOrUnderTheConfiguredLimit() throws Exception {
    ContentLengthLimitFilter filter = new ContentLengthLimitFilter(properties());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    request.setContent(new byte[10]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void allowsRequestsWithNoContentLength() throws Exception {
    ContentLengthLimitFilter filter = new ContentLengthLimitFilter(properties());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

}
