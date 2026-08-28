package com.mel.cb.memory;

/**
 * Ephemeral, session-scoped conversation storage -- backed by Redis ({@link RedisConversationMemoryStore})
 * under any non-{@code local} profile. Deliberately excludes {@code local} the same way
 * {@code com.mel.cb.provider.QuotaTracker} does: {@link ConversationMemoryService} takes this via
 * {@code ObjectProvider} and no-ops when it's absent, so the local no-infra profile is unaffected.
 */
public interface ConversationMemoryStore {

  ConversationContext load(String conversationId);

  void appendTurn(String conversationId, String userId, ConversationTurn userTurn, ConversationTurn assistantTurn);

  /** Drops the oldest {@code turnsRemoved} stored turns and replaces the rolling summary. */
  void applyEviction(String conversationId, String newSummary, int turnsRemoved);

  /** Conversations for this user with activity inside the last {@code sessionTtl}. */
  long activeConversationCount(String userId);

}
