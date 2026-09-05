package com.mel.cb.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Builds the {@link Prompt} sent to a {@link ChatProvider}: the system prompt, an optional rolling
 * summary of older conversation turns evicted by {@code com.mel.cb.memory.ConversationMemoryService},
 * the still-verbatim recent history, and the new user message. Public (not package-private) because
 * {@code ChatReplyService} -- outside this package -- is the one assembling prompts now; providers
 * just execute whatever {@link Prompt} they're handed.
 * <p>
 * Deliberately carries no {@code ChatOptions} of its own (Phase 3 tried attaching tool callbacks
 * here via the {@code Prompt}'s runtime options; reverted). Each provider's own
 * {@code OpenAiChatModel} already has its complete default options (model, baseUrl, apiKey, and now
 * tool callbacks) baked in at construction; leaving {@code Prompt.getOptions()} {@code null} is what
 * makes the model fall back to those defaults, per its own {@code buildRequestPrompt} logic. A
 * non-null runtime options object here would replace that provider's whole configuration instead of
 * merging with it.
 * <p>
 * {@code facts} are the top-K most semantically-relevant durable
 * {@code UserFact}s for this user and message, from {@code
 * com.mel.cb.memory.ConversationMemoryService#findRelevantFacts} -- injected the same way as
 * {@code summary}, a separate {@code SystemMessage} rather than folded into the system prompt
 * itself, so it's clearly scoped as retrieved context rather than baked-in instructions.
 */
public final class ChatPrompts {

  private ChatPrompts() {
  }

  public static Prompt of(String systemPrompt, String summary, List<String> facts, List<Message> history,
      String userMessage) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    if (summary != null && !summary.isBlank()) {
      messages.add(new SystemMessage("Summary of the earlier part of this conversation: " + summary));
    }
    if (facts != null && !facts.isEmpty()) {
      messages.add(new SystemMessage("Known facts about this user:\n"
          + facts.stream().map(f -> "- " + f).collect(Collectors.joining("\n"))));
    }
    messages.addAll(history);
    messages.add(new UserMessage(userMessage));
    return new Prompt(messages);
  }

}
