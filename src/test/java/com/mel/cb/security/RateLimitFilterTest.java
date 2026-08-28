package com.mel.cb.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private static RateLimiterRegistry registryAllowingOnePerMinute() {
    RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(1)
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO)
        .build();
    return RateLimiterRegistry.of(config);
  }

  private static SecurityProperties enabled() {
    SecurityProperties properties = new SecurityProperties();
    properties.getRateLimit().setEnabled(true);
    return properties;
  }

  @Test
  void allowsFirstRequestThenRejectsSecondFromTheSameClient() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabled(), registryAllowingOnePerMinute());

    MockHttpServletRequest first = new MockHttpServletRequest("POST", "/bot/chat/reply");
    first.setRemoteAddr("10.0.0.1");
    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
    filter.doFilter(first, firstResponse, new MockFilterChain());
    assertThat(firstResponse.getStatus()).isEqualTo(200);

    MockHttpServletRequest second = new MockHttpServletRequest("POST", "/bot/chat/reply");
    second.setRemoteAddr("10.0.0.1");
    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilter(second, secondResponse, new MockFilterChain());

    assertThat(secondResponse.getStatus()).isEqualTo(429);
    assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void tracksDifferentClientsIndependently() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabled(), registryAllowingOnePerMinute());

    MockHttpServletRequest clientA = new MockHttpServletRequest("POST", "/bot/chat/reply");
    clientA.setRemoteAddr("10.0.0.1");
    MockHttpServletResponse responseA = new MockHttpServletResponse();
    filter.doFilter(clientA, responseA, new MockFilterChain());

    MockHttpServletRequest clientB = new MockHttpServletRequest("POST", "/bot/chat/reply");
    clientB.setRemoteAddr("10.0.0.2");
    MockHttpServletResponse responseB = new MockHttpServletResponse();
    filter.doFilter(clientB, responseB, new MockFilterChain());

    assertThat(responseA.getStatus()).isEqualTo(200);
    assertThat(responseB.getStatus()).isEqualTo(200);
  }

  @Test
  void prefersXForwardedForOverRemoteAddr() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabled(), registryAllowingOnePerMinute());

    MockHttpServletRequest first = new MockHttpServletRequest("POST", "/bot/chat/reply");
    first.setRemoteAddr("10.0.0.1");
    first.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
    filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

    // Same forwarded client through a different proxy hop -- still counted as the same client.
    MockHttpServletRequest second = new MockHttpServletRequest("POST", "/bot/chat/reply");
    second.setRemoteAddr("10.0.0.9");
    second.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.9");
    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilter(second, secondResponse, new MockFilterChain());

    assertThat(secondResponse.getStatus()).isEqualTo(429);
  }

  @Test
  void skipsEnforcementWhenDisabled() throws Exception {
    SecurityProperties properties = new SecurityProperties();
    properties.getRateLimit().setEnabled(false);
    RateLimitFilter filter = new RateLimitFilter(properties, registryAllowingOnePerMinute());

    for (int i = 0; i < 3; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
      request.setRemoteAddr("10.0.0.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

}
