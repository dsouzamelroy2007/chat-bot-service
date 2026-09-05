package com.mel.cb.memory;

import com.mel.cb.provider.ChatPrompts;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.util.ChatDataUtil;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Folds the conversation turns {@link ConversationMemoryService} is evicting into the rolling
 * summary, and separately spots any durable facts about the user worth keeping in
 * {@link UserFact} beyond the session. Reuses {@link ProviderRouter} (same failover/quota-aware
 * routing as a normal reply) rather than a dedicated client -- this is just another chat
 * completion, on whichever free-tier provider is available.
 */
@Slf4j
@Component
public class ConversationSummarizer {

  private static final String SYSTEM_PROMPT = """
      You maintain a running summary of an ongoing chat conversation and a list of durable facts
      about the user (preferences, name, ongoing projects, anything worth remembering beyond this
      session). Respond with ONLY minified JSON, no markdown fences, no commentary, in exactly this
      shape: {"summary":"...","facts":["...","..."]}. "facts" must contain only NEW facts learned
      from the conversation excerpt below, not ones already covered by the existing summary; use an
      empty array if there are none.
      """;

  private final ProviderRouter providerRouter;

  public ConversationSummarizer(ProviderRouter providerRouter) {
    this.providerRouter = providerRouter;
  }

  public SummarizationResult summarize(String existingSummary, List<ConversationTurn> turnsToFold) {
    String transcript = turnsToFold.stream()
        .map(turn -> turn.role() + ": " + turn.content())
        .collect(Collectors.joining("\n"));
    String instruction = """
        Existing summary: %s

        Conversation excerpt to fold into the summary:
        %s
        """.formatted(existingSummary == null || existingSummary.isBlank() ? "(none yet)" : existingSummary, transcript);

    Prompt prompt = ChatPrompts.of(SYSTEM_PROMPT, null, List.of(), List.of(), instruction);
    try {
      ChatResponse response = providerRouter.getReply(prompt).response();
      String text = response.getResult() != null ? response.getResult().getOutput().getText() : null;
      return parse(text, existingSummary);
    } catch (Exception e) {
      // The eviction itself (freeing space) must still proceed even if summarization fails -- see
      // ConversationMemoryService -- so this falls back to keeping the prior summary unchanged and
      // simply not extracting new facts from the folded-in turns, rather than blocking eviction.
      log.warn("Conversation summarization failed, keeping prior summary and skipping fact extraction: {}", e.getMessage());
      return new SummarizationResult(existingSummary, List.of());
    }
  }

  private SummarizationResult parse(String text, String fallbackSummary) {
    if (text == null || text.isBlank()) {
      return new SummarizationResult(fallbackSummary, List.of());
    }
    try {
      Json parsed = ChatDataUtil.fromJson(stripMarkdownFences(text), Json.class);
      String summary = parsed.summary != null && !parsed.summary.isBlank() ? parsed.summary : fallbackSummary;
      List<String> facts = parsed.facts != null ? parsed.facts : List.of();
      return new SummarizationResult(summary, facts);
    } catch (Exception e) {
      log.warn("Could not parse summarization response as JSON, keeping prior summary: {}", e.getMessage());
      return new SummarizationResult(fallbackSummary, List.of());
    }
  }

  private static String stripMarkdownFences(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "");
    }
    return trimmed.trim();
  }

  @Data
  private static class Json {
    private String summary;
    private List<String> facts;
  }

}
