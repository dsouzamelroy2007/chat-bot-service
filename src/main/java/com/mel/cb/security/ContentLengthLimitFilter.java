package com.mel.cb.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registered on {@code /chat/**} only. Rejects an oversized body before Jackson ever reads it, off
 * the {@code Content-Length} header -- cheap, and independent of {@link com.mel.cb.model.ChatMessage}'s
 * own {@code @Size} validation, which only runs after the whole body has already been parsed.
 * A request with no {@code Content-Length} (e.g. chunked transfer-encoding) can't be checked this
 * way and is let through -- the {@code @Size} validation on the parsed message is what catches that
 * case instead, just after the body has been read rather than before.
 */
@RequiredArgsConstructor
public class ContentLengthLimitFilter extends OncePerRequestFilter {

  private final SecurityProperties properties;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long contentLength = request.getContentLengthLong();
    if (contentLength > properties.getMaxRequestBytes()) {
      SecurityResponses.reject(request, response, HttpStatus.CONTENT_TOO_LARGE,
          "Request body exceeds the maximum allowed size");
      return;
    }
    chain.doFilter(request, response);
  }

}
