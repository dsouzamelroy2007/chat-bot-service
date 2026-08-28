package com.mel.cb.tools;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the tool beans, resolving each {@code api-key-env} to its actual key here rather than
 * inside the tool classes themselves -- same separation {@code com.mel.cb.provider.ProviderConfig}
 * uses for {@code OpenAiCompatibleProvider}, so a tool class stays a plain, directly-testable POJO
 * that just takes an already-resolved key (or {@code null}) rather than doing its own environment
 * lookups.
 */
@Configuration
public class ToolsConfig {

  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  @Bean
  public GeocodingClient geocodingClient(ToolsProperties properties) {
    return new GeocodingClient(restClient(properties.getGeocodingBaseUrl(), null, null));
  }

  @Bean
  public WeatherTools weatherTools(GeocodingClient geocodingClient, ToolsProperties properties) {
    return new WeatherTools(geocodingClient, restClient(properties.getWeather().getBaseUrl(), null, null));
  }

  @Bean
  public TimeTools timeTools(GeocodingClient geocodingClient) {
    return new TimeTools(geocodingClient);
  }

  @Bean
  public TransitTools transitTools(GeocodingClient geocodingClient, ToolsProperties properties) {
    String apiKeyEnv = properties.getTransit().getApiKeyEnv();
    String apiKey = System.getenv(apiKeyEnv);
    return new TransitTools(geocodingClient, restClient(properties.getTransit().getBaseUrl(), null, null), apiKey, apiKeyEnv);
  }

  @Bean
  public WebSearchTools webSearchTools(ToolsProperties properties) {
    String apiKeyEnv = properties.getWebSearch().getApiKeyEnv();
    String apiKey = System.getenv(apiKeyEnv);
    return new WebSearchTools(restClient(properties.getWebSearch().getBaseUrl(), "X-Subscription-Token", apiKey), apiKey, apiKeyEnv);
  }

  private static RestClient restClient(String baseUrl, String authHeaderName, String authHeaderValue) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
    if (authHeaderName != null && authHeaderValue != null && !authHeaderValue.isBlank()) {
      builder.defaultHeader(authHeaderName, authHeaderValue);
    }
    builder.defaultHeader(HttpHeaders.ACCEPT, "application/json");
    return builder.build();
  }

}
