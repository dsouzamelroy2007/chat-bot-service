package com.mel.cb.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
    return new WebSearchTools(RestClient.builder().baseUrl(searchServer.baseUrl()).build(), apiKey, "TAVILY_API_KEY");
  }

  @Test
  void returnsFormattedTopResults() {
    searchServer.stubFor(post(urlPathEqualTo("/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"query":"spring ai","results":[
              {"title":"Result One","url":"https://example.com/1","content":"First result"},
              {"title":"Result Two","url":"https://example.com/2","content":"Second result"}
            ]}
            """)));

    String result = webSearchTools("test-key").searchWeb("spring ai");

    assertTrue(result.contains("Result One"));
    assertTrue(result.contains("https://example.com/1"));
    assertTrue(result.contains("Result Two"));
  }

  @Test
  void sendsApiKeyAndQueryInRequestBody() {
    searchServer.stubFor(post(urlPathEqualTo("/search"))
        .withRequestBody(equalToJson("{\"api_key\":\"test-key\",\"query\":\"spring ai\",\"max_results\":5}"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));

    webSearchTools("test-key").searchWeb("spring ai");

    searchServer.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo("/search")));
  }

  @Test
  void returnsFriendlyMessageWhenNoResults() {
    searchServer.stubFor(post(urlPathEqualTo("/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));

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
