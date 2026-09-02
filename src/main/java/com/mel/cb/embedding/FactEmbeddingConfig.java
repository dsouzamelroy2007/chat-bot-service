package com.mel.cb.embedding;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires {@link FactEmbeddingService}, resolving {@code api-key-env} here rather than inside the
 * service class itself -- same separation {@code com.mel.cb.tools.ToolsConfig} and
 * {@code com.mel.cb.provider.ProviderConfig} use. Builds a plain {@link RestClient} against
 * Gemini's OpenAI-compatible {@code /embeddings} endpoint rather than Spring AI's
 * {@code OpenAiEmbeddingModel} -- see {@link FactEmbeddingService}'s own doc for the real,
 * live-found incompatibility (a missing {@code index} field) that made the SDK-based approach
 * unusable against this specific endpoint.
 * <p>
 * Timeout (8s) is deliberately much shorter than {@code OpenAiCompatibleProvider}'s 80s chat
 * timeout: unlike fact <em>persistence</em> (already-accepted synchronous eviction path, rare),
 * fact <em>retrieval</em> now runs inside {@code ChatReplyService.buildPrompt} on every request,
 * inside the same 60s {@code chatReply} resilience4j {@code TimeLimiter} that wraps the whole
 * synchronous reply -- a stalled embedding call needs to fail fast, not silently eat most of that
 * budget on a nice-to-have augmentation.
 */
@Configuration
public class FactEmbeddingConfig {

  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  @Bean
  public FactEmbeddingService factEmbeddingService(FactEmbeddingProperties properties) {
    String apiKey = System.getenv(properties.getApiKeyEnv());
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    RestClient restClient = RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
    return new FactEmbeddingService(restClient, apiKey, properties.getApiKeyEnv(),
        properties.getModel(), properties.getDimensions());
  }

}
