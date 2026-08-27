package com.mel.cb.provider;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

final class ChatPrompts {

  private ChatPrompts() {
  }

  static Prompt of(String systemPrompt, String userMessage) {
    return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)));
  }

}
