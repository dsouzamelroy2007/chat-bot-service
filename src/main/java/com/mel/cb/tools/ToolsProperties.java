package com.mel.cb.tools;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code chatbot.tools.*}. Weather and time need no API key at all (Open-Meteo's APIs are free,
 * keyless, and shared for geocoding by every tool here); transit and web-search do, and follow the
 * same optional/self-disabling convention as {@code chatbot.anthropic} in
 * {@code com.mel.cb.provider} -- {@link ToolsConfig} resolves each {@code api-key-env} and the tool
 * disables itself (see {@link ChatTool#isEnabled()}) rather than being offered to the model unable
 * to actually do anything.
 */
@Data
@ConfigurationProperties(prefix = "chatbot.tools")
public class ToolsProperties {

  private String geocodingBaseUrl = "https://geocoding-api.open-meteo.com";

  private Weather weather = new Weather();
  private Transit transit = new Transit();
  private WebSearch webSearch = new WebSearch();

  @Data
  public static class Weather {
    private String baseUrl = "https://api.open-meteo.com";
  }

  @Data
  public static class Transit {
    private String baseUrl = "https://api.openrouteservice.org";
    private String apiKeyEnv = "OPENROUTESERVICE_API_KEY";
  }

  @Data
  public static class WebSearch {
    private String baseUrl = "https://api.search.brave.com";
    private String apiKeyEnv = "BRAVE_SEARCH_API_KEY";
  }

}
