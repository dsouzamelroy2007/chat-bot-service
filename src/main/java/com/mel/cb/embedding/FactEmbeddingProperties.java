package com.mel.cb.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code chatbot.embedding.*} -- reuses Gemini's already-configured OpenAI-compatible endpoint
 * (same base URL/{@code GEMINI_API_KEY} used for chat, {@code chatbot.providers}) rather than a
 * separate provider, since embeddings are a nice-to-have augmentation to the reply, not the core
 * path. {@link FactEmbeddingConfig} resolves {@code api-key-env} and the service disables itself
 * (see {@link FactEmbeddingService#isEnabled()}) when unset, same convention as
 * {@code com.mel.cb.tools.ChatTool}. {@code dimensions} must match the {@code vector(N)} width in
 * {@code V2__add_embedding_to_user_facts.sql} exactly.
 */
@Data
@ConfigurationProperties(prefix = "chatbot.embedding")
public class FactEmbeddingProperties {

  private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
  private String model = "gemini-embedding-001";
  private String apiKeyEnv = "GEMINI_API_KEY";
  private int dimensions = 768;
  private int topK = 5;

}
