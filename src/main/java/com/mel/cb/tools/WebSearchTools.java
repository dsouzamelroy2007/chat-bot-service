package com.mel.cb.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

/**
 * General web search via the Brave Search API's free tier -- chosen over the various zero-key
 * "instant answer" APIs (e.g. DuckDuckGo's) because those only cover direct/instant answers, not
 * general web results, which is what a "web search" tool needs to be useful. Optional: off (its
 * {@link #isEnabled()} returns false) unless an API key is configured, same convention as
 * {@code com.mel.cb.provider.AnthropicChatProvider}.
 */
@Slf4j
public class WebSearchTools implements ChatTool {

  private static final int MAX_RESULTS = 5;

  private final RestClient restClient;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public WebSearchTools(RestClient restClient, String apiKey, String apiKeyEnv) {
    this.restClient = restClient;
    if (apiKey == null || apiKey.isBlank()) {
      enabled.set(false);
      log.warn("Web search tool disabled: no API key configured (env var {} not set)", apiKeyEnv);
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled.get();
  }

  @Tool(description = "Search the web for up-to-date information not already known, and return the top results.")
  public String searchWeb(@ToolParam(description = "The search query") String query) {
    SearchResponse response = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/res/v1/web/search")
            .queryParam("q", query)
            .queryParam("count", MAX_RESULTS)
            .build())
        .retrieve()
        .body(SearchResponse.class);
    if (response == null || response.web() == null || response.web().results() == null || response.web().results().isEmpty()) {
      return "No web results found for \"" + query + "\".";
    }

    List<Result> results = response.web().results();
    return IntStream.range(0, Math.min(MAX_RESULTS, results.size()))
        .mapToObj(i -> {
          Result r = results.get(i);
          return "%d. %s\n%s\n%s".formatted(i + 1, r.title(), r.url(), r.description());
        })
        .collect(Collectors.joining("\n\n"));
  }

  private record SearchResponse(@JsonProperty("web") WebResults web) {
  }

  private record WebResults(@JsonProperty("results") List<Result> results) {
  }

  private record Result(
      @JsonProperty("title") String title,
      @JsonProperty("url") String url,
      @JsonProperty("description") String description) {
  }

}
