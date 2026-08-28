package com.mel.cb.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registered on {@code /chat/**} only. Keyed by client IP (first hop of {@code X-Forwarded-For} if
 * present, else the socket's remote address) via {@link RateLimiterRegistry#rateLimiter(String)},
 * which creates a new limiter for a never-before-seen key on first use and reuses it after --
 * independent of {@link com.mel.cb.provider.ProviderRouter}'s own per-provider rate limiters, which
 * protect the free-tier provider quotas, not the service's own front door.
 * <p>
 * The registry keeps one limiter per distinct client key for the life of the process, unbounded --
 * acceptable for this project's self-hosted, low-to-moderate-traffic scope, but worth flagging: a
 * deployment seeing a very large number of distinct client IPs over a long uptime would grow this
 * map without eviction. Not fixed here, since bounding it (an LRU/TTL cache of limiters) is real
 * complexity for a scenario this project isn't yet sized for.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final SecurityProperties properties;
  private final RateLimiterRegistry registry;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!properties.getRateLimit().isEnabled()) {
      chain.doFilter(request, response);
      return;
    }
    RateLimiter rateLimiter = registry.rateLimiter(clientKey(request));
    if (!rateLimiter.acquirePermission()) {
      response.setHeader("Retry-After", "60");
      SecurityResponses.reject(request, response, HttpStatus.TOO_MANY_REQUESTS,
          "Rate limit exceeded, please slow down");
      return;
    }
    chain.doFilter(request, response);
  }

  private static String clientKey(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

}
