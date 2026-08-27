package com.mel.cb.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisQuotaTrackerTest {

  @Container
  static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private RedisQuotaTracker quotaTracker;

  @BeforeAll
  static void startRedisTemplate() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void stopRedisTemplate() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    quotaTracker = new RedisQuotaTracker(redisTemplate);
  }

  @Test
  void notOverQuotaBeforeAnyUsageIsRecorded() {
    assertFalse(quotaTracker.isOverQuota("groq", new ProviderLimits(10, 1000)));
  }

  @Test
  void notOverQuotaUnderNinetyPercentOfEitherCap() {
    quotaTracker.recordUsage("groq", 100);
    quotaTracker.recordUsage("groq", 100);

    assertFalse(quotaTracker.isOverQuota("groq", new ProviderLimits(10, 100_000)));
  }

  @Test
  void overQuotaAtNinetyPercentOfRequestCap() {
    ProviderLimits limits = new ProviderLimits(10, 1_000_000);
    for (int i = 0; i < 9; i++) {
      quotaTracker.recordUsage("groq", 1);
    }

    assertTrue(quotaTracker.isOverQuota("groq", limits));
  }

  @Test
  void overQuotaAtNinetyPercentOfTokenCap() {
    quotaTracker.recordUsage("groq", 95);

    assertTrue(quotaTracker.isOverQuota("groq", new ProviderLimits(1_000_000, 100)));
  }

  @Test
  void tracksEachProviderIndependently() {
    ProviderLimits limits = new ProviderLimits(10, 1_000_000);
    for (int i = 0; i < 9; i++) {
      quotaTracker.recordUsage("groq", 1);
    }

    assertTrue(quotaTracker.isOverQuota("groq", limits));
    assertFalse(quotaTracker.isOverQuota("gemini", limits));
  }

}
