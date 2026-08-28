package com.mel.cb.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registered on {@code /chat/**} only. {@code chatbot.security.api-key} (env {@code CHATBOT_API_KEY})
 * follows this project's usual optional/self-disabling convention -- unset means every request is
 * let through unauthenticated, same as today, which is the right default for a fresh self-host
 * before the operator has configured anything. When set, the {@code X-API-Key} header must match
 * exactly (compared with {@link MessageDigest#isEqual}, which runs in constant time regardless of
 * where the first mismatching byte is, unlike {@code String.equals}).
 * <p>
 * This is a single shared secret between the operator's own client(s) and this API, not per-user
 * auth -- {@code userId} in the request body is still just a client-supplied string nobody
 * independently verifies. For the widget specifically (a static HTML/JS file, possibly hosted
 * publicly on Vercel per Phase 4), baking this key into the page means anyone who views the page
 * source can read it -- it stops anonymous scripts/bots that don't bother, and third parties on a
 * different origin (still gated by Phase 4's CORS allow-list), but it is not a secret from a
 * determined visitor of the widget itself. True per-visitor auth would be a much larger scope
 * (accounts, OAuth/JWT) than this phase's brief called for.
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-API-Key";

  private final SecurityProperties properties;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String configuredKey = properties.getApiKey();
    if (configuredKey == null || configuredKey.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    String providedKey = request.getHeader(HEADER);
    if (providedKey == null || !MessageDigest.isEqual(
        configuredKey.getBytes(StandardCharsets.UTF_8), providedKey.getBytes(StandardCharsets.UTF_8))) {
      SecurityResponses.reject(request, response, HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
      return;
    }
    chain.doFilter(request, response);
  }

}
