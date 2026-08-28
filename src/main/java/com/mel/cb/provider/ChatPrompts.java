package com.mel.cb.provider;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Builds the {@link Prompt} sent to a {@link ChatProvider}: the system prompt, an optional rolling
 * summary of older conversation turns evicted by {@code com.mel.cb.memory.ConversationMemoryService},
 * the still-verbatim recent history, and the new user message. Public (not package-private) because
 * {@code ChatReplyService} -- outside this package -- is the one assembling prompts now; providers
 * just execute whatever {@link Prompt} they're handed (see docs/PLAN.md Phase 2 notes).
 */
public final class ChatPrompts {

  private ChatPrompts() {
  }

  public static Prompt of(String systemPrompt, String summary, List<Message> history, String userMessage) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    if (summary != null && !summary.isBlank()) {
      messages.add(new SystemMessage("Summary of the earlier part of this conversation: " + summary));
    }
    messages.addAll(history);
    messages.add(new UserMessage(userMessage));
    return new Prompt(messages);
  }

}
