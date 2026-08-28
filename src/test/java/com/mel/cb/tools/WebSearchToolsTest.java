package com.mel.cb.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class WebSearchToolsTest {

  @RegisterExtension
  static WireMockExtension searchServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    searchServer.resetAll();
  }

  private WebSearchTools webSearchTools(String apiKey) {
    return new WebSearchTools(RestClient.builder().baseUrl(searchServer.baseUrl()).build(), apiKey, "BRAVE_SEARCH_API_KEY");
  }

  @Test
  void returnsFormattedTopResults() {
    searchServer.stubFor(get(urlPathEqualTo("/res/v1/web/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"web":{"results":[
              {"title":"Result One","url":"https://example.com/1","description":"First result"},
              {"title":"Result Two","url":"https://example.com/2","description":"Second result"}
            ]}}
            """)));

    String result = webSearchTools("test-key").searchWeb("spring ai");

    assertTrue(result.contains("Result One"));
    assertTrue(result.contains("https://example.com/1"));
    assertTrue(result.contains("Result Two"));
  }

  @Test
  void returnsFriendlyMessageWhenNoResults() {
    searchServer.stubFor(get(urlPathEqualTo("/res/v1/web/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"web\":{\"results\":[]}}")));

    String result = webSearchTools("test-key").searchWeb("asdkjhaksjdhaksjhd");

    assertEquals("No web results found for \"asdkjhaksjdhaksjhd\".", result);
  }

  @Test
  void disabledWithoutApiKey() {
    assertFalse(webSearchTools(null).isEnabled());
  }

  @Test
  void enabledWithApiKey() {
    assertTrue(webSearchTools("a-real-key").isEnabled());
  }

}
