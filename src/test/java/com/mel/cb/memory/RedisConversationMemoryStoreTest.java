package com.mel.cb.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.time.Instant;
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
class RedisConversationMemoryStoreTest {

  @Container
  static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private RedisConversationMemoryStore store;

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
    ConversationMemoryProperties properties = new ConversationMemoryProperties();
    properties.setSessionTtl(Duration.ofHours(8));
    store = new RedisConversationMemoryStore(redisTemplate, properties);
  }

  @Test
  void loadOnUnknownConversationReturnsEmptyContext() {
    ConversationContext context = store.load("unknown-conv");

    assertNull(context.summary());
    assertTrue(context.turns().isEmpty());
    assertEquals(0L, context.tokenCount());
  }

  @Test
  void appendTurnStoresBothMessagesInOrderAndAccumulatesTokens() {
    store.appendTurn("conv-1", "user-1", turn(ConversationTurn.ROLE_USER, "hello there"),
        turn(ConversationTurn.ROLE_ASSISTANT, "hi, how can I help?"));

    ConversationContext context = store.load("conv-1");

    assertEquals(2, context.turns().size());
    assertEquals("hello there", context.turns().get(0).content());
    assertEquals("hi, how can I help?", context.turns().get(1).content());
    assertTrue(context.tokenCount() > 0);
  }

  @Test
  void activeConversationCountReflectsRegisteredConversationsForThatUserOnly() {
    store.appendTurn("conv-1", "user-1", turn(ConversationTurn.ROLE_USER, "a"), turn(ConversationTurn.ROLE_ASSISTANT, "b"));
    store.appendTurn("conv-2", "user-1", turn(ConversationTurn.ROLE_USER, "c"), turn(ConversationTurn.ROLE_ASSISTANT, "d"));
    store.appendTurn("conv-3", "user-2", turn(ConversationTurn.ROLE_USER, "e"), turn(ConversationTurn.ROLE_ASSISTANT, "f"));

    assertEquals(2, store.activeConversationCount("user-1"));
    assertEquals(1, store.activeConversationCount("user-2"));
    assertEquals(0, store.activeConversationCount("user-with-no-conversations"));
  }

  @Test
  void applyEvictionDropsOldestTurnsAndReplacesSummaryAndTokenCount() {
    store.appendTurn("conv-1", "user-1", turn(ConversationTurn.ROLE_USER, "first"), turn(ConversationTurn.ROLE_ASSISTANT, "first-reply"));
    store.appendTurn("conv-1", "user-1", turn(ConversationTurn.ROLE_USER, "second"), turn(ConversationTurn.ROLE_ASSISTANT, "second-reply"));
    ConversationContext before = store.load("conv-1");
    assertEquals(4, before.turns().size());

    store.applyEviction("conv-1", "summary of the first exchange", 2);

    ConversationContext after = store.load("conv-1");
    assertEquals(2, after.turns().size());
    assertEquals("second", after.turns().get(0).content());
    assertEquals("second-reply", after.turns().get(1).content());
    assertEquals("summary of the first exchange", after.summary());
    assertTrue(after.tokenCount() > 0);
  }

  private static ConversationTurn turn(String role, String content) {
    return new ConversationTurn(role, content, Instant.now());
  }

}
