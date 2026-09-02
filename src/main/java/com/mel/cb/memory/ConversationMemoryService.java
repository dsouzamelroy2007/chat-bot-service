package com.mel.cb.memory;

import com.mel.cb.embedding.FactEmbeddingProperties;
import com.mel.cb.embedding.FactEmbeddingService;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates conversation memory for {@code ChatReplyService}: loads context to build the next
 * prompt, records each turn, evicts (summarizes + frees space) once a conversation crosses its
 * share of its user's token budget, and retrieves the most relevant durable facts for the current
 * message (RAG follow-up, docs/PLAN.md). {@link ConversationMemoryStore}, {@link UserFactRepository},
 * and {@link ConversationSummarizer} are taken via {@code ObjectProvider} and this no-ops when
 * they're absent -- the same pattern {@code com.mel.cb.provider.QuotaTracker} uses -- so the
 * {@code local} profile (no Redis/Postgres) keeps working with plain stateless single-turn replies,
 * unchanged from Phase 1. {@link FactEmbeddingService} is different: always a bean (like
 * {@code com.mel.cb.tools.ChatTool}s), self-disabling via its own {@code isEnabled()} when
 * {@code GEMINI_API_KEY} is unset -- conditional on an API key's presence, not a profile, so it's
 * injected directly rather than via {@code ObjectProvider}.
 */
@Slf4j
@Service
public class ConversationMemoryService {

  private final ConversationMemoryStore store;
  private final UserFactRepository userFactRepository;
  private final ConversationSummarizer summarizer;
  private final FactEmbeddingService factEmbeddingService;
  private final FactEmbeddingProperties factEmbeddingProperties;
  private final ConversationMemoryProperties properties;

  public ConversationMemoryService(
      ObjectProvider<ConversationMemoryStore> store,
      ObjectProvider<UserFactRepository> userFactRepository,
      ObjectProvider<ConversationSummarizer> summarizer,
      FactEmbeddingService factEmbeddingService,
      FactEmbeddingProperties factEmbeddingProperties,
      ConversationMemoryProperties properties) {
    this.store = store.getIfAvailable();
    this.userFactRepository = userFactRepository.getIfAvailable();
    this.summarizer = summarizer.getIfAvailable();
    this.factEmbeddingService = factEmbeddingService;
    this.factEmbeddingProperties = factEmbeddingProperties;
    this.properties = properties;
  }

  /**
   * Semantic retrieval for the current message (RAG follow-up, docs/PLAN.md) -- embeds
   * {@code userMessage}, then finds the top-K most similar stored facts for {@code userId} by
   * cosine distance. Empty (never {@code null}) when the repository is absent (the {@code local}
   * profile), embedding is disabled/unconfigured, or the embedding call itself fails -- this is an
   * augmentation to the reply, not the reply itself, so it degrades silently rather than failing the
   * request.
   */
  public List<String> findRelevantFacts(String userId, String userMessage) {
    if (userFactRepository == null || factEmbeddingService == null || !factEmbeddingService.isEnabled()) {
      return List.of();
    }
    float[] queryEmbedding = factEmbeddingService.embed(userMessage);
    if (queryEmbedding == null) {
      return List.of();
    }
    return userFactRepository.findMostSimilar(userId, FactEmbeddingService.toPgVectorLiteral(queryEmbedding),
            factEmbeddingProperties.getTopK())
        .stream()
        .map(UserFact::getFact)
        .toList();
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
        UserFact saved = userFactRepository.save(new UserFact(null, userId, fact.trim(), conversationId, now, now));
        embedAndStore(saved);
      } catch (DataIntegrityViolationException e) {
        log.debug("Fact already recorded for user {}: {}", userId, fact);
      }
    }
  }

  /**
   * Best-effort: a failed/skipped embedding leaves the fact saved as plain text, just absent from
   * future semantic retrieval until re-embedded -- there's no backfill job for this, a deliberate
   * scope boundary (RAG follow-up, docs/PLAN.md). Never allowed to affect the outer save/dedup path
   * above, which is why this runs after {@code save()} has already succeeded, not as part of it.
   */
  private void embedAndStore(UserFact saved) {
    if (factEmbeddingService == null || !factEmbeddingService.isEnabled()) {
      return;
    }
    float[] embedding = factEmbeddingService.embed(saved.getFact());
    if (embedding != null) {
      userFactRepository.updateEmbedding(saved.getId(), FactEmbeddingService.toPgVectorLiteral(embedding));
    }
  }

}
