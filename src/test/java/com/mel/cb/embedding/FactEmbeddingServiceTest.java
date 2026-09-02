package com.mel.cb.embedding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/**
 * WireMock-backed, same convention as {@code com.mel.cb.tools.WebSearchToolsTest}. The stubbed
 * response deliberately matches Gemini's real (confirmed live) shape -- no {@code index}, no
 * {@code usage} in each {@code data} item, unlike real OpenAI's API -- rather than a "should be
 * OpenAI-compatible" assumption; a stub with those fields present would have hidden the actual
 * bug this class's own doc describes (the OpenAI SDK's strict model choking on their absence),
 * exactly the kind of gap only a real round trip catches, per this project's established habit.
 */
class FactEmbeddingServiceTest {

  @RegisterExtension
  static WireMockExtension embeddingServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    embeddingServer.resetAll();
  }

  private FactEmbeddingService service(String apiKey) {
    RestClient restClient = RestClient.builder().baseUrl(embeddingServer.baseUrl()).build();
    return new FactEmbeddingService(restClient, apiKey, "GEMINI_API_KEY", "test-embedding-model", 768);
  }

  @Test
  void embedsSuccessfully() {
    embeddingServer.stubFor(post(urlPathEqualTo("/embeddings"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {
              "object": "list",
              "data": [{"object": "embedding", "embedding": [0.1, 0.2, 0.3]}],
              "model": "test-embedding-model"
            }
            """)));

    float[] result = service("test-key").embed("likes coffee");

    assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, result);
  }

  @Test
  void disabledWithoutApiKey() {
    assertFalse(service(null).isEnabled());
    assertNull(service(null).embed("likes coffee"));
  }

  @Test
  void enabledWithApiKey() {
    assertTrue(service("test-key").isEnabled());
  }

  @Test
  void embedReturnsNullOnBlankInput() {
    assertNull(service("test-key").embed(""));
    assertNull(service("test-key").embed(null));
  }

  @Test
  void embedReturnsNullOnServerError() {
    embeddingServer.stubFor(post(urlPathEqualTo("/embeddings"))
        .willReturn(aResponse().withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"message\":\"internal error\"}}")));

    assertNull(service("test-key").embed("likes coffee"));
  }

  @Test
  void toPgVectorLiteralFormatsAsPgvectorTextLiteral() {
    assertEquals("[0.1,-0.2,3.0]", FactEmbeddingService.toPgVectorLiteral(new float[] {0.1f, -0.2f, 3.0f}));
  }

}
