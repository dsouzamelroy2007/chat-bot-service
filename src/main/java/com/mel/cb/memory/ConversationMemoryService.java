package com.mel.cb.memory;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates conversation memory for {@code ChatReplyService}: loads context to build the next
 * prompt, records each turn, and evicts (summarizes + frees space) once a conversation crosses its
 * share of its user's token budget. All three collaborators ({@link ConversationMemoryStore},
 * {@link UserFactRepository}, {@link ConversationSummarizer}) are taken via {@code ObjectProvider}
 * and this no-ops when they're absent -- the same pattern {@code com.mel.cb.provider.QuotaTracker}
 * uses -- so the {@code local} profile (no Redis/Postgres) keeps working with plain stateless
 * single-turn replies, unchanged from Phase 1.
 */
@Slf4j
@Service
public class ConversationMemoryService {

  private final ConversationMemoryStore store;
  private final UserFactRepository userFactRepository;
  private final ConversationSummarizer summarizer;
  private final ConversationMemoryProperties properties;

  public ConversationMemoryService(
      ObjectProvider<ConversationMemoryStore> store,
      ObjectProvider<UserFactRepository> userFactRepository,
      ObjectProvider<ConversationSummarizer> summarizer,
      ConversationMemoryProperties properties) {
    this.store = store.getIfAvailable();
    this.userFactRepository = userFactRepository.getIfAvailable();
    this.summarizer = summarizer.getIfAvailable();
    this.properties = properties;
  }

  public ConversationContext loadContext(String conversationId) {
    return store != null ? store.load(conversationId) : ConversationContext.empty();
  }

  public void recordTurn(String conversationId, String userId, String userMessage, String assistantMessage) {
    if (store == null) {
      return;
    }
    Instant now = Instant.now();
    store.appendTurn(conversationId, userId,
        new ConversationTurn(ConversationTurn.ROLE_USER, userMessage, now),
        new ConversationTurn(ConversationTurn.ROLE_ASSISTANT, assistantMessage, now));
    maybeEvict(conversationId, userId);
  }

  private void maybeEvict(String conversationId, String userId) {
    long activeConversations = Math.max(1, store.activeConversationCount(userId));
    long conversationBudget = properties.getUserTokenBudget() / activeConversations;
    long threshold = (long) (conversationBudget * properties.getEvictionThreshold());

    ConversationContext context = store.load(conversationId);
    int keep = properties.getMinRawTurnsKept();
    if (context.tokenCount() < threshold || context.turns().size() <= keep) {
      return;
    }

    List<ConversationTurn> toFold = context.turns().subList(0, context.turns().size() - keep);
    log.info("Conversation {} at {} estimated tokens (budget {} across {} active conversations for user {}); "
            + "folding {} oldest turns into the summary",
        conversationId, context.tokenCount(), conversationBudget, activeConversations, userId, toFold.size());

    SummarizationResult result = summarizer != null
        ? summarizer.summarize(context.summary(), toFold)
        : new SummarizationResult(context.summary(), List.of());

    store.applyEviction(conversationId, result.summary(), toFold.size());
    persistFacts(userId, conversationId, result.facts());
  }

  private void persistFacts(String userId, String conversationId, List<String> facts) {
    if (userFactRepository == null || facts == null || facts.isEmpty()) {
      return;
    }
    for (String fact : facts) {
      if (fact == null || fact.isBlank()) {
        continue;
      }
      try {
        Instant now = Instant.now();
        userFactRepository.save(new UserFact(null, userId, fact.trim(), conversationId, now, now));
      } catch (DataIntegrityViolationException e) {
        log.debug("Fact already recorded for user {}: {}", userId, fact);
      }
    }
  }

}
