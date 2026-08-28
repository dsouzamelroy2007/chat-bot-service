package com.mel.cb.provider;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Builds the {@link Prompt} sent to a {@link ChatProvider}: the system prompt, an optional rolling
 * summary of older conversation turns evicted by {@code com.mel.cb.memory.ConversationMemoryService},
 * the still-verbatim recent history, the new user message, and (Phase 3) whichever tool callbacks
 * {@code com.mel.cb.tools.ChatToolsRegistry} currently has enabled. Public (not package-private)
 * because {@code ChatReplyService} -- outside this package -- is the one assembling prompts now;
 * providers just execute whatever {@link Prompt} they're handed (see docs/PLAN.md Phase 2 notes).
 * Attaching tools is done via the Prompt's own runtime {@link ChatOptions} rather than each
 * provider's baked-in options -- {@code OpenAiChatModel}/{@code AnthropicChatModel} merge the two at
 * call time and run the tool-execution loop automatically, so no provider or {@link ProviderRouter}
 * change was needed for tool-calling to work (confirmed against the actual Spring AI 2.0.0 jars, not
 * assumed, given this project's history of stack-freshness surprises -- see docs/PLAN.md).
 */
public final class ChatPrompts {

  private ChatPrompts() {
  }

  public static Prompt of(String systemPrompt, String summary, List<Message> history, String userMessage) {
    return of(systemPrompt, summary, history, userMessage, List.of());
  }

  public static Prompt of(String systemPrompt, String summary, List<Message> history, String userMessage,
      List<ToolCallback> toolCallbacks) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    if (summary != null && !summary.isBlank()) {
      messages.add(new SystemMessage("Summary of the earlier part of this conversation: " + summary));
    }
    messages.addAll(history);
    messages.add(new UserMessage(userMessage));

    if (toolCallbacks == null || toolCallbacks.isEmpty()) {
      return new Prompt(messages);
    }
    ChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();
    return new Prompt(messages, options);
  }

}
