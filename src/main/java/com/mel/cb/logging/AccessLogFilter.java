package com.mel.cb.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registered on {@code /*}, outermost (lowest order) among this project's filters -- so it wraps
 * every other filter and the controller/servlet chain, and sees each response's true final status
 * whether the request reached a controller or was rejected upstream (e.g. by
 * {@code com.mel.cb.security.RateLimitFilter}/{@code ApiKeyAuthFilter}). One INFO line per request
 * for every endpoint, not just {@code /chat/**} -- unlike {@code ChatReplyController}/
 * {@code ChatReplyService}'s own per-request logs (which carry business fields like
 * {@code userId}/{@code conversationId} for chat requests specifically), this is uniform
 * infrastructure-level visibility: method, path, status, and latency for anything the app serves,
 * including the static widget and {@code /actuator/**}. Structured JSON output (outside the
 * {@code local} profile, see {@code application.yml}) makes {@code method}/{@code path}/
 * {@code status}/{@code durationMs} independently searchable/filterable in Render's log viewer.
 */
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000;
      log.info("access_log method={} path={} status={} durationMs={}",
          request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
    }
  }

}
