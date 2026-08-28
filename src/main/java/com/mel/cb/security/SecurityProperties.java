package com.mel.cb.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code chatbot.security.*} (Phase 5, docs/PLAN.md). {@code apiKey} follows the same
 * optional/self-disabling convention as every provider/tool key in this project -- unset means the
 * check is skipped entirely (logged once at startup by {@link SecurityFilterConfig}), not a fatal
 * misconfiguration, since a from-scratch self-host should boot without any secrets configured.
 */
@Data
@ConfigurationProperties(prefix = "chatbot.security")
public class SecurityProperties {

  /** Shared secret required via the {@code X-API-Key} header on {@code /chat/**} when set. */
  private String apiKey;

  /** Rejects a request to {@code /chat/**} up front (413) if its Content-Length exceeds this. */
  private long maxRequestBytes = 8192;

  private RateLimit rateLimit = new RateLimit();

  @Data
  public static class RateLimit {
    private boolean enabled = true;
    private int requestsPerMinute = 20;
  }

}
