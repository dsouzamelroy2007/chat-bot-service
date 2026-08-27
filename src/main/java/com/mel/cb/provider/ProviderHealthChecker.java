package com.mel.cb.provider;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Pings each provider once at startup so an unreachable or unconfigured provider is logged and
 * disabled up front, rather than discovered mid-request by {@link ProviderRouter}.
 */
@Slf4j
@Component
@Profile("!local")
public class ProviderHealthChecker implements ApplicationRunner {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final ChatProviderRegistry registry;

  public ProviderHealthChecker(ChatProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void run(ApplicationArguments args) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();

    for (ChatProvider provider : registry.all()) {
      if (!provider.isEnabled()) {
        log.info("Provider {} startup health check skipped: already disabled", provider.getProviderId());
        continue;
      }
      boolean healthy = provider.checkHealth(restClient);
      log.info("Provider {} startup health check: {}", provider.getProviderId(), healthy ? "reachable" : "disabled");
    }
  }

}
