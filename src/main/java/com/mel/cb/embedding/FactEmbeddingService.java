package com.mel.cb.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Calls Gemini's OpenAI-compatible {@code /embeddings} endpoint directly via {@link RestClient}
 * rather than through Spring AI's {@code OpenAiEmbeddingModel} --
 * real bug found live, not assumed from the "OpenAI-compatible" label: a real request/response
 * round trip showed Gemini's actual response omits both {@code index} and {@code usage} entirely
 * from each item in the {@code data} array (confirmed via a raw call, not the SDK), unlike real
 * OpenAI's API which always includes them. The OpenAI Java SDK's Kotlin-generated response model
 * treats {@code index} as required and throws ({@code `index` is not set}) the moment anything
 * reads it -- observed live as every single embedding call failing despite the request itself
 * succeeding with a real 200 and a real, correctly-sized (and {@code dimensions}-honoring)
 * embedding vector already present in the response body. This mirrors this project's own
 * established pattern for exactly this class of gap ({@code com.mel.cb.tools.WeatherTools}/
 * {@code WebSearchTools} already call their free-tier APIs directly via {@link RestClient} with a
 * hand-written response record, rather than trusting a heavier client's assumptions) -- and this
 * project's own repeated lesson that a compatibility claim needs a real round trip to trust, not
 * just documentation (a lesson this project has hit more than once before).
 */
@Slf4j
public class FactEmbeddingService {

  private final RestClient restClient;
  private final String apiKey;
  private final String model;
  private final int dimensions;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public FactEmbeddingService(RestClient restClient, String apiKey, String apiKeyEnv, String model, int dimensions) {
    this.restClient = restClient;
    this.apiKey = apiKey != null ? apiKey : "";
    this.model = model;
    this.dimensions = dimensions;
    if (apiKey == null || apiKey.isBlank()) {
      enabled.set(false);
      log.warn("Fact embedding disabled: no API key configured (env var {} not set)", apiKeyEnv);
    }
  }

  public boolean isEnabled() {
    return enabled.get();
  }

  /**
   * Never throws. Returns {@code null} when disabled, given blank input, or the underlying call
   * fails for any reason -- the single fail-open chokepoint both {@code ConversationMemoryService}
   * call sites (persisting a new fact's embedding, retrieving relevant facts for a query) rely on.
   */
  public float[] embed(String text) {
    if (!enabled.get() || text == null || text.isBlank()) {
      return null;
    }
    try {
      EmbeddingResponse response = restClient.post()
          .uri("/embeddings")
          .header("Authorization", "Bearer " + apiKey)
          .body(Map.of("input", text, "model", model, "dimensions", dimensions))
          .retrieve()
          .body(EmbeddingResponse.class);
      if (response == null || response.data() == null || response.data().isEmpty()) {
        log.warn("Embedding call returned no data");
        return null;
      }
      return response.data().getFirst().embedding();
    } catch (Exception e) {
      log.warn("Embedding call failed: {}", e.getMessage());
      return null;
    }
  }

  /** Formats a vector as pgvector's text literal ({@code "[0.01,-0.02,...]"}) for the native
   * queries in {@code UserFactRepository} -- avoids needing a {@code pgvector-jdbc} dependency
   * just to pass a query/insert parameter. */
  public static String toPgVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(embedding[i]);
    }
    return sb.append(']').toString();
  }

  /** Deliberately loose: only the two fields this app actually uses are mapped. Gemini's response
   * omits {@code index}/{@code usage} entirely (see class doc) -- mapping only {@code embedding}
   * means that's a non-issue here, unlike the strict OpenAI SDK model this replaced. */
  private record EmbeddingResponse(@JsonProperty("data") List<EmbeddingData> data) {
  }

  private record EmbeddingData(@JsonProperty("embedding") float[] embedding) {
  }

}
