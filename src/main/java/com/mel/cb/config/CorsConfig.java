package com.mel.cb.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables cross-origin calls to {@code /chat/**} -- needed from Phase 4 onward, since the widget
 * can now be hosted separately (Vercel) from this API rather than served same-origin. Origins are
 * a plain allow-list, not a wildcard: this endpoint takes no auth/cookies today, but hardening
 * exactly who can call it (beyond "which origins") is explicitly Phase 5's job, not this one's.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${chatbot.cors.allowed-origins}")
  private List<String> allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/chat/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST")
        // X-API-Key (Phase 5, docs/PLAN.md) -- needed for a cross-origin widget to pass
        // chatbot.security.api-key through preflight when it's configured.
        .allowedHeaders("Content-Type", "X-API-Key");
  }

}
