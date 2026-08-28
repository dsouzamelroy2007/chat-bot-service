package com.mel.cb.memory;

import com.mel.cb.util.ChatDataUtil;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Keys: {@code chatbot:memory:conv:<id>:turns} (a Redis list of JSON {@link ConversationTurn}s),
 * {@code ...:summary}, {@code ...:tokens}, and {@code chatbot:memory:user:<userId>:conversations}
 * (a sorted set of conversation ids scored by last-activity epoch millis -- self-cleaning: every
 * read of {@link #activeConversationCount} first drops entries older than {@code sessionTtl}
 * rather than relying on Redis to expire individual sorted-set members, which it can't).
 * <p>
 * Token counts are a cheap character-based estimate ({@link #estimateTokens}), not a real
 * tokenizer -- accurate enough for budgeting when the context is "full", not for billing (real
 * provider usage is what {@code com.mel.cb.provider.QuotaTracker} tracks). {@link #applyEviction}
 * recomputes the stored count from scratch off the post-eviction summary + remaining turns rather
 * than subtracting, so estimation error can't accumulate turn over turn.
 */
@Component
@Profile("!local")
public class RedisConversationMemoryStore implements ConversationMemoryStore {

  private final StringRedisTemplate redisTemplate;
  private final ConversationMemoryProperties properties;

  public RedisConversationMemoryStore(StringRedisTemplate redisTemplate, ConversationMemoryProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public ConversationContext load(String conversationId) {
    List<String> raw = redisTemplate.opsForList().range(turnsKey(conversationId), 0, -1);
    List<ConversationTurn> turns = (raw == null ? List.<String>of() : raw).stream()
        .map(json -> ChatDataUtil.fromJson(json, ConversationTurn.class))
        .toList();
    String summary = redisTemplate.opsForValue().get(summaryKey(conversationId));
    long tokens = currentTokenCount(conversationId);
    return new ConversationContext(summary, turns, tokens);
  }

  @Override
  public void appendTurn(String conversationId, String userId, ConversationTurn userTurn, ConversationTurn assistantTurn) {
    redisTemplate.opsForList().rightPush(turnsKey(conversationId), ChatDataUtil.getObjectAsString(userTurn));
    redisTemplate.opsForList().rightPush(turnsKey(conversationId), ChatDataUtil.getObjectAsString(assistantTurn));
    long added = estimateTokens(userTurn.content()) + estimateTokens(assistantTurn.content());
    redisTemplate.opsForValue().increment(tokensKey(conversationId), added);

    refreshTtl(turnsKey(conversationId));
    refreshTtl(tokensKey(conversationId));
    if (Boolean.TRUE.equals(redisTemplate.hasKey(summaryKey(conversationId)))) {
      refreshTtl(summaryKey(conversationId));
    }

    String userConvKey = userConversationsKey(userId);
    redisTemplate.opsForZSet().add(userConvKey, conversationId, Instant.now().toEpochMilli());
    refreshTtl(userConvKey);
  }

  @Override
  public void applyEviction(String conversationId, String newSummary, int turnsRemoved) {
    for (int i = 0; i < turnsRemoved; i++) {
      redisTemplate.opsForList().leftPop(turnsKey(conversationId));
    }
    redisTemplate.opsForValue().set(summaryKey(conversationId), newSummary == null ? "" : newSummary);
    refreshTtl(summaryKey(conversationId));

    List<String> remaining = redisTemplate.opsForList().range(turnsKey(conversationId), 0, -1);
    long tokens = estimateTokens(newSummary);
    if (remaining != null) {
      for (String json : remaining) {
        tokens += estimateTokens(ChatDataUtil.fromJson(json, ConversationTurn.class).content());
      }
    }
    redisTemplate.opsForValue().set(tokensKey(conversationId), String.valueOf(tokens));
    refreshTtl(tokensKey(conversationId));
    refreshTtl(turnsKey(conversationId));
  }

  @Override
  public long activeConversationCount(String userId) {
    String key = userConversationsKey(userId);
    long cutoff = Instant.now().minus(properties.getSessionTtl()).toEpochMilli();
    redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff);
    Long count = redisTemplate.opsForZSet().zCard(key);
    return count == null ? 0L : count;
  }

  private long currentTokenCount(String conversationId) {
    String value = redisTemplate.opsForValue().get(tokensKey(conversationId));
    return value != null ? Long.parseLong(value) : 0L;
  }

  private void refreshTtl(String key) {
    redisTemplate.expire(key, properties.getSessionTtl());
  }

  private static long estimateTokens(String text) {
    return text == null || text.isBlank() ? 0L : Math.max(1, text.length() / 4);
  }

  private static String turnsKey(String conversationId) {
    return "chatbot:memory:conv:%s:turns".formatted(conversationId);
  }

  private static String summaryKey(String conversationId) {
    return "chatbot:memory:conv:%s:summary".formatted(conversationId);
  }

  private static String tokensKey(String conversationId) {
    return "chatbot:memory:conv:%s:tokens".formatted(conversationId);
  }

  private static String userConversationsKey(String userId) {
    return "chatbot:memory:user:%s:conversations".formatted(userId);
  }

}
