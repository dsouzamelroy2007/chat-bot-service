package com.mel.cb.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Keys counters by provider + UTC calendar day ({@code chatbot:quota:<id>:<yyyyMMdd>:requests|tokens})
 * so midnight-UTC rollover is just "the key changes" -- no scheduled reset job needed. A short TTL
 * is set on first increment of each key purely to bound Redis memory growth, not for correctness.
 */
@Slf4j
@Component
@Profile("!local")
public class RedisQuotaTracker implements QuotaTracker {

  private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
  private static final double QUOTA_WARN_THRESHOLD = 0.9;
  private static final Duration KEY_TTL = Duration.ofDays(2);

  private final StringRedisTemplate redisTemplate;

  public RedisQuotaTracker(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean isOverQuota(String providerId, ProviderLimits limits) {
    long requests = currentCount(requestsKey(providerId));
    long tokens = currentCount(tokensKey(providerId));
    boolean over = requests >= limits.requestsPerDay() * QUOTA_WARN_THRESHOLD
        || tokens >= limits.tokensPerDay() * QUOTA_WARN_THRESHOLD;
    if (over) {
      log.info("Provider {} at/above 90% of daily quota (requests={}/{}, tokens={}/{})",
          providerId, requests, limits.requestsPerDay(), tokens, limits.tokensPerDay());
    }
    return over;
  }

  @Override
  public void recordUsage(String providerId, long tokensUsed) {
    increment(requestsKey(providerId), 1);
    if (tokensUsed > 0) {
      increment(tokensKey(providerId), tokensUsed);
    }
  }

  private long currentCount(String key) {
    String value = redisTemplate.opsForValue().get(key);
    return value != null ? Long.parseLong(value) : 0L;
  }

  private void increment(String key, long delta) {
    Long newValue = redisTemplate.opsForValue().increment(key, delta);
    if (newValue != null && newValue == delta) {
      redisTemplate.expire(key, KEY_TTL);
    }
  }

  private String requestsKey(String providerId) {
    return "chatbot:quota:%s:%s:requests".formatted(providerId, today());
  }

  private String tokensKey(String providerId) {
    return "chatbot:quota:%s:%s:tokens".formatted(providerId, today());
  }

  private String today() {
    return DAY_KEY.format(Instant.now());
  }

}
