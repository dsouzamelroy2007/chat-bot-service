package com.mel.cb.security;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Phase 5 hardening filters (docs/PLAN.md) as {@link FilterRegistrationBean}s, scoped and
 * ordered explicitly rather than left to component-scan order:
 * <ol>
 *   <li>{@link SecurityHeadersFilter} on {@code /*} -- runs first so its headers land on every
 *       response, including ones the filters below reject.</li>
 *   <li>{@link ContentLengthLimitFilter} on {@code /chat/*} -- cheapest check, before touching any
 *       rate-limiter state.</li>
 *   <li>{@link RateLimitFilter} on {@code /chat/*} -- before the API-key check, so key-guessing
 *       attempts are throttled too, not just legitimate traffic.</li>
 *   <li>{@link ApiKeyAuthFilter} on {@code /chat/*} -- last, since it's the most expensive check to
 *       be worth gating on (though in practice all four are cheap).</li>
 * </ol>
 * Filter {@code urlPatterns} are relative to the servlet context path ({@code /bot}), so
 * {@code /chat/*} correctly means {@code /bot/chat/*}, matching {@link com.mel.cb.config.CorsConfig}'s
 * {@code /chat/**} Spring MVC mapping (a different pattern language, same effective scope).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityFilterConfig {

  private final SecurityProperties properties;

  @PostConstruct
  void logAuthStatus() {
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      log.warn("chatbot.security.api-key (CHATBOT_API_KEY) is not set -- /chat/** is reachable "
          + "without authentication. Set it before exposing this service publicly.");
    }
  }

  @Bean
  public RateLimiterRegistry chatRateLimiterRegistry() {
    RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(properties.getRateLimit().getRequestsPerMinute())
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO)
        .build();
    return RateLimiterRegistry.of(config);
  }

  @Bean
  public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
    FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(-100);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<ContentLengthLimitFilter> contentLengthLimitFilter() {
    FilterRegistrationBean<ContentLengthLimitFilter> registration =
        new FilterRegistrationBean<>(new ContentLengthLimitFilter(properties));
    registration.addUrlPatterns("/chat/*");
    registration.setOrder(-50);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiterRegistry chatRateLimiterRegistry) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(properties, chatRateLimiterRegistry));
    registration.addUrlPatterns("/chat/*");
    registration.setOrder(-40);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter() {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(new ApiKeyAuthFilter(properties));
    registration.addUrlPatterns("/chat/*");
    registration.setOrder(-30);
    return registration;
  }

}
