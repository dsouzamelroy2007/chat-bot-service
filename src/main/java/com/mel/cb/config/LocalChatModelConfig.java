package com.mel.cb.config;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalChatModelConfig {

  @Bean
  public ChatModel chatModel() {
    return (Prompt prompt) -> new ChatResponse(
        List.of(new Generation(new AssistantMessage("This is a stubbed AI reply for local testing."))));
  }

}
