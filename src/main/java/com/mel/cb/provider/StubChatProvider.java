package com.mel.cb.provider;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Sole provider under the {@code local} profile: no infra, no API key, a canned reply. Replaces
 * the old {@code LocalChatModelConfig} global-{@code ChatModel}-bean-override mechanism now that
 * providers are a routed list rather than a single Spring AI bean.
 */
@Component
@Profile("local")
public class StubChatProvider implements ChatProvider {

  static final String STUB_REPLY = "This is a stubbed AI reply for local testing.";

  @Override
  public String getProviderId() {
    return "stub";
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void disable(String reason) {
    // the local stub is always available; nothing to disable
  }

  @Override
  public ProviderLimits getLimits() {
    return null;
  }

  @Override
  public ChatResponse reply(Prompt prompt) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(STUB_REPLY))));
  }

  @Override
  public Flux<ChatResponse> streamReply(Prompt prompt) {
    return Flux.just(reply(prompt));
  }

}
