package com.mel.cb.provider;

import com.anthropic.models.messages.Model;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Optional, non-default paid fallback. Off unless {@code chatbot.anthropic.enabled=true}; when
 * enabled it's given the lowest priority so the free-tier providers in {@code chatbot.providers}
 * are always tried first. Built programmatically (same reasoning as
 * {@link OpenAiCompatibleProvider}) rather than via Spring AI's Anthropic autoconfiguration, so a
 * default boot needs no {@code ANTHROPIC_API_KEY} at all -- that autoconfiguration is excluded in
 * application.yml.
 */
@Slf4j
@Component
@Profile("!local")
@ConditionalOnProperty(prefix = "chatbot.anthropic", name = "enabled", havingValue = "true")
public class AnthropicChatProvider implements ChatProvider {

  private final AnthropicChatModel chatModel;
  private final int priority;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public AnthropicChatProvider(
      @Value("${chatbot.anthropic.model:claude-sonnet-5}") String model,
      @Value("${chatbot.anthropic.api-key-env:ANTHROPIC_API_KEY}") String apiKeyEnv,
      @Value("${chatbot.anthropic.priority:1000}") int priority) {
    this.priority = priority;
    String apiKey = System.getenv(apiKeyEnv);
    this.chatModel = AnthropicChatModel.builder()
        .options(AnthropicChatOptions.builder()
            .apiKey(apiKey != null ? apiKey : "")
            .model(Model.of(model))
            // this is the last provider ProviderRouter ever tries, but keep the SDK's own
            // retry-on-429/5xx out of the way for consistency with OpenAiCompatibleProvider.
            .maxRetries(0)
            .build())
        .build();
    if (apiKey == null || apiKey.isBlank()) {
      disable("no API key configured (env var " + apiKeyEnv + " not set)");
    }
  }

  @Override
  public String getProviderId() {
    return "anthropic";
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
      log.warn("Provider anthropic disabled: {}", reason);
    }
  }

  @Override
  public ProviderLimits getLimits() {
    return null;
  }

  @Override
  public ChatResponse reply(Prompt prompt) {
    return chatModel.call(prompt);
  }

  @Override
  public Flux<ChatResponse> streamReply(Prompt prompt) {
    return chatModel.stream(prompt);
  }

}
