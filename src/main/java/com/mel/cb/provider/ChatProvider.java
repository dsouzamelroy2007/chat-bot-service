package com.mel.cb.provider;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

/**
 * A single LLM backend the {@link ProviderRouter} can route a chat request to. Implementations
 * cover the free-tier OpenAI-compatible APIs ({@link OpenAiCompatibleProvider}), the optional
 * paid Anthropic fallback ({@link AnthropicChatProvider}), and the local no-key stub
 * ({@link StubChatProvider}).
 */
public interface ChatProvider {

  String getProviderId();

  /** Lower values are tried first. */
  int getPriority();

  boolean isEnabled();

  /** Marks the provider unusable (e.g. no API key, failed health check) until restart. */
  void disable(String reason);

  /** Daily request/token caps for free-tier quota tracking, or {@code null} if untracked. */
  ProviderLimits getLimits();

  ChatResponse reply(String systemPrompt, String userMessage);

  Flux<ChatResponse> streamReply(String systemPrompt, String userMessage);

  /** Startup reachability check; only {@link OpenAiCompatibleProvider} pings anything real. */
  default boolean checkHealth(RestClient restClient) {
    return true;
  }

}
