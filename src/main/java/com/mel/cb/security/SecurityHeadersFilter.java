package com.mel.cb.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies to every response (registered on {@code /*}) -- baseline headers that cost nothing and
 * help regardless of what's behind them. {@code Cache-Control: no-store} is scoped to
 * {@code /chat/**} only, since a chat reply is per-user/per-conversation and should never be
 * cached, while the static widget under {@code /} legitimately wants normal caching.
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    if (request.getRequestURI().contains("/chat/")) {
      response.setHeader("Cache-Control", "no-store");
    }
    chain.doFilter(request, response);
  }

}
