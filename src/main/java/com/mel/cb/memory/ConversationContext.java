package com.mel.cb.memory;

import java.util.List;

/**
 * A conversation's current in-memory state: the rolling summary of whatever's been evicted so far
 * (null until the first eviction), the still-verbatim recent turns, and an approximate token count
 * of both combined (see {@link ConversationMemoryStore} for how it's estimated) -- what
 * {@link ConversationMemoryService} checks against the per-conversation budget.
 */
public record ConversationContext(String summary, List<ConversationTurn> turns, long tokenCount) {

  public static ConversationContext empty() {
    return new ConversationContext(null, List.of(), 0L);
  }

}
