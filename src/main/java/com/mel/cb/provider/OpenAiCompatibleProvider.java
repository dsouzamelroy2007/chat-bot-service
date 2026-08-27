package com.mel.cb.provider;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

/**
 * A {@link ChatProvider} backed by any OpenAI-compatible chat completions API (Gemini, Groq,
 * Cerebras, Mistral, OpenRouter, ...). The {@link OpenAiChatModel} is built programmatically from
 * the {@code chatbot.providers} config entry rather than via Spring AI's OpenAI autoconfiguration
 * -- that autoconfiguration only supports a single, statically-configured client and eagerly
 * builds unrelated embedding/image/moderation/audio clients that fail without a global
 * {@code OPENAI_API_KEY} (see docs/PLAN.md, Phase 0 notes).
 */
@Slf4j
public class OpenAiCompatibleProvider implements ChatProvider {

  private final String providerId;
  private final int priority;
  private final String baseUrl;
  private final String apiKey;
  private final ProviderLimits limits;
  private final OpenAiChatModel chatModel;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public OpenAiCompatibleProvider(ProviderProperties properties, String apiKey) {
    this.providerId = properties.getId();
    this.priority = properties.getPriority();
    this.baseUrl = properties.getBaseUrl();
    this.apiKey = apiKey != null ? apiKey : "";
    this.limits = properties.getLimits() != null
        ? new ProviderLimits(properties.getLimits().getRequestsPerDay(), properties.getLimits().getTokensPerDay())
        : null;
    this.chatModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
            .baseUrl(this.baseUrl)
            .apiKey(this.apiKey)
            .model(properties.getModel())
            // ProviderRouter already fails over to the next provider on any error, including
            // 429/5xx -- letting the OpenAI SDK's own default (3 retries, with backoff) run first
            // would keep hammering an already-rate-limited provider instead of moving on.
            .maxRetries(0)
            .build())
        .build();
    if (this.apiKey.isBlank()) {
      disable("no API key configured (env var " + properties.getApiKeyEnv() + " not set)");
    }
  }

  @Override
  public String getProviderId() {
    return providerId;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public boolean isEnabled() {
    return enabled.get();
  }

  @Override
  public void disable(String reason) {
    if (enabled.compareAndSet(true, false)) {
      log.warn("Provider {} disabled: {}", providerId, reason);
    }
  }

  @Override
  public ProviderLimits getLimits() {
    return limits;
  }

  @Override
  public ChatResponse reply(String systemPrompt, String userMessage) {
    return chatModel.call(ChatPrompts.of(systemPrompt, userMessage));
  }

  @Override
  public Flux<ChatResponse> streamReply(String systemPrompt, String userMessage) {
    return chatModel.stream(ChatPrompts.of(systemPrompt, userMessage));
  }

  @Override
  public boolean checkHealth(RestClient restClient) {
    try {
      restClient.get()
          .uri(baseUrl + "/models")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (Exception e) {
      disable("startup health check against " + baseUrl + "/models failed: " + e.getMessage());
      return false;
    }
  }

}
