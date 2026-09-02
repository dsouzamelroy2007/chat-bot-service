package com.mel.cb.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registered at order -200 -- below (so it runs before, and wraps) every filter in
 * {@code com.mel.cb.security.SecurityFilterConfig} (lowest there is -100), so
 * {@link AccessLogFilter} observes the true final response status even for a request rejected by
 * a security filter, not just ones that reach a controller.
 */
@Configuration
public class AccessLogFilterConfig {

  @Bean
  public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
    FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(new AccessLogFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(-200);
    return registration;
  }

}
