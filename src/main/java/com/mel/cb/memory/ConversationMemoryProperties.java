package com.mel.cb.memory;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code chatbot.memory.*} -- see docs/PLAN.md Phase 2 notes for the reasoning behind these
 * defaults (a sliding per-conversation session, and a per-user token budget split evenly across
 * that user's currently-active conversations).
 */
@Data
@ConfigurationProperties(prefix = "chatbot.memory")
public class ConversationMemoryProperties {

  /** Sliding TTL applied to a conversation's stored turns/summary on every new turn. */
  private Duration sessionTtl = Duration.ofHours(8);

  /** Total approximate context tokens one user is allowed across all their active conversations. */
  private long userTokenBudget = 10_000;

  /** Fraction of a conversation's share of the user budget that triggers eviction, once crossed. */
  private double evictionThreshold = 0.9;

  /** Most recent turns (user+assistant messages counted separately) always kept verbatim, never summarized. */
  private int minRawTurnsKept = 4;

}
