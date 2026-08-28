package com.mel.cb.memory;

import java.time.Instant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/** One stored turn of a conversation, kept verbatim in {@link ConversationMemoryStore} until evicted. */
public record ConversationTurn(String role, String content, Instant timestamp) {

  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";

  /** Maps back to the Spring AI message type {@code com.mel.cb.provider.ChatPrompts} expects. */
  public Message toMessage() {
    return ROLE_ASSISTANT.equals(role) ? new AssistantMessage(content) : new UserMessage(content);
  }

}
