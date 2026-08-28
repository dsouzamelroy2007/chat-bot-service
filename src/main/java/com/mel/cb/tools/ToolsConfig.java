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
 * lookups. Every tool here authenticates in its own request (a query param for OpenRouteService, a
 * JSON body field for Tavily) rather than a shared header, so the {@link RestClient} built here
 * carries no auth of its own.
 */
@Configuration
public class ToolsConfig {

  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  @Bean
  public GeocodingClient geocodingClient(ToolsProperties properties) {
    return new GeocodingClient(restClient(properties.getGeocodingBaseUrl()));
  }

  @Bean
  public WeatherTools weatherTools(GeocodingClient geocodingClient, ToolsProperties properties) {
    return new WeatherTools(geocodingClient, restClient(properties.getWeather().getBaseUrl()));
  }

  @Bean
  public TimeTools timeTools(GeocodingClient geocodingClient) {
    return new TimeTools(geocodingClient);
  }

  @Bean
  public TransitTools transitTools(GeocodingClient geocodingClient, ToolsProperties properties) {
    String apiKeyEnv = properties.getTransit().getApiKeyEnv();
    String apiKey = System.getenv(apiKeyEnv);
    return new TransitTools(geocodingClient, restClient(properties.getTransit().getBaseUrl()), apiKey, apiKeyEnv);
  }

  @Bean
  public WebSearchTools webSearchTools(ToolsProperties properties) {
    String apiKeyEnv = properties.getWebSearch().getApiKeyEnv();
    String apiKey = System.getenv(apiKeyEnv);
    return new WebSearchTools(restClient(properties.getWebSearch().getBaseUrl()), apiKey, apiKeyEnv);
  }

  private static RestClient restClient(String baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
        .build();
  }

}
