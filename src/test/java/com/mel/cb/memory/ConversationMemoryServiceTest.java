package com.mel.cb.memory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mel.cb.embedding.FactEmbeddingProperties;
import com.mel.cb.embedding.FactEmbeddingService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

  @Mock
  private ConversationMemoryStore store;

  @Mock
  private UserFactRepository userFactRepository;

  @Mock
  private ConversationSummarizer summarizer;

  @Mock
  private FactEmbeddingService factEmbeddingService;

  @Mock
  private ObjectProvider<ConversationMemoryStore> storeProvider;

  @Mock
  private ObjectProvider<UserFactRepository> userFactRepositoryProvider;

  @Mock
  private ObjectProvider<ConversationSummarizer> summarizerProvider;

  private ConversationMemoryProperties properties;
  private FactEmbeddingProperties factEmbeddingProperties;

  @BeforeEach
  void setUp() {
    properties = new ConversationMemoryProperties();
    properties.setUserTokenBudget(1000);
    properties.setEvictionThreshold(0.9);
    properties.setMinRawTurnsKept(2);
    factEmbeddingProperties = new FactEmbeddingProperties();
    factEmbeddingProperties.setTopK(5);
  }

  // factEmbeddingService is an unstubbed mock by default here: isEnabled() returns false
  // (Mockito's default for an unstubbed boolean method), so embedAndStore/findRelevantFacts
  // short-circuit before touching anything else -- matches "disabled/unconfigured" for every
  // existing test below that doesn't specifically exercise the embedding-enabled path.
  private ConversationMemoryService serviceWithStore() {
    when(storeProvider.getIfAvailable()).thenReturn(store);
    when(userFactRepositoryProvider.getIfAvailable()).thenReturn(userFactRepository);
    when(summarizerProvider.getIfAvailable()).thenReturn(summarizer);
    return new ConversationMemoryService(storeProvider, userFactRepositoryProvider, summarizerProvider,
        factEmbeddingService, factEmbeddingProperties, properties);
  }

  @Test
  void noOpsWhenStoreAbsentLikeUnderLocalProfile() {
    when(storeProvider.getIfAvailable()).thenReturn(null);
    when(userFactRepositoryProvider.getIfAvailable()).thenReturn(null);
    when(summarizerProvider.getIfAvailable()).thenReturn(null);
    ConversationMemoryService service = new ConversationMemoryService(storeProvider, userFactRepositoryProvider,
        summarizerProvider, factEmbeddingService, factEmbeddingProperties, properties);

    ConversationContext context = service.loadContext("conv-1");
    assertEquals(ConversationContext.empty(), context);
    assertDoesNotThrow(() -> service.recordTurn("conv-1", "user-1", "hi", "hello"));
    assertEquals(List.of(), service.findRelevantFacts("user-1", "hi"));
  }

  @Test
  void recordTurnAppendsButDoesNotEvictWhenUnderBudget() {
    ConversationMemoryService service = serviceWithStore();
    when(store.load("conv-1")).thenReturn(new ConversationContext(null, List.of(turn(), turn()), 10L));
    when(store.activeConversationCount("user-1")).thenReturn(1L);

    service.recordTurn("conv-1", "user-1", "hi", "hello");

    verify(store).appendTurn(eq("conv-1"), eq("user-1"), any(), any());
    verify(store, never()).applyEviction(anyString(), anyString(), anyInt());
    verify(userFactRepository, never()).save(any());
  }

  @Test
  void evictsAndPersistsFactsWhenOverBudget() {
    ConversationMemoryService service = serviceWithStore();
    List<ConversationTurn> turns = List.of(turn(), turn(), turn(), turn());
    when(store.load("conv-1")).thenReturn(new ConversationContext("old summary", turns, 950L));
    when(store.activeConversationCount("user-1")).thenReturn(1L);
    when(summarizer.summarize(eq("old summary"), any())).thenReturn(
        new SummarizationResult("new summary", List.of("likes coffee", "based in Amsterdam")));

    service.recordTurn("conv-1", "user-1", "hi", "hello");

    verify(store).applyEviction("conv-1", "new summary", 2);
    verify(userFactRepository, times(2)).save(any());
  }

  @Test
  void splitsUserTokenBudgetAcrossActiveConversationsForThatUser() {
    ConversationMemoryService service = serviceWithStore();
    List<ConversationTurn> turns = List.of(turn(), turn(), turn(), turn());
    // budget 1000 / 2 active conversations = 500; threshold 0.9 * 500 = 450; 460 tokens crosses it.
    when(store.load("conv-1")).thenReturn(new ConversationContext(null, turns, 460L));
    when(store.activeConversationCount("user-1")).thenReturn(2L);
    when(summarizer.summarize(any(), any())).thenReturn(new SummarizationResult("summary", List.of()));

    service.recordTurn("conv-1", "user-1", "hi", "hello");

    verify(store).applyEviction(eq("conv-1"), eq("summary"), eq(2));
  }

  @Test
  void evictionStillFreesSpaceWhenSummarizerIsAbsent() {
    when(storeProvider.getIfAvailable()).thenReturn(store);
    when(userFactRepositoryProvider.getIfAvailable()).thenReturn(userFactRepository);
    when(summarizerProvider.getIfAvailable()).thenReturn(null);
    ConversationMemoryService service = new ConversationMemoryService(storeProvider, userFactRepositoryProvider,
        summarizerProvider, factEmbeddingService, factEmbeddingProperties, properties);

    List<ConversationTurn> turns = List.of(turn(), turn(), turn(), turn());
    when(store.load("conv-1")).thenReturn(new ConversationContext("old summary", turns, 950L));
    when(store.activeConversationCount("user-1")).thenReturn(1L);

    service.recordTurn("conv-1", "user-1", "hi", "hello");

    verify(store).applyEviction("conv-1", "old summary", 2);
    verify(userFactRepository, never()).save(any());
  }

  @Test
  void findRelevantFactsEmptyWhenRepositoryAbsent() {
    when(storeProvider.getIfAvailable()).thenReturn(store);
    when(userFactRepositoryProvider.getIfAvailable()).thenReturn(null);
    when(summarizerProvider.getIfAvailable()).thenReturn(summarizer);
    ConversationMemoryService service = new ConversationMemoryService(storeProvider, userFactRepositoryProvider,
        summarizerProvider, factEmbeddingService, factEmbeddingProperties, properties);

    assertEquals(List.of(), service.findRelevantFacts("user-1", "what do I like?"));
  }

  @Test
  void findRelevantFactsEmptyWhenEmbeddingDisabled() {
    ConversationMemoryService service = serviceWithStore();
    // factEmbeddingService is unstubbed -- isEnabled() defaults to false.

    assertEquals(List.of(), service.findRelevantFacts("user-1", "what do I like?"));
    verify(userFactRepository, never()).findMostSimilar(any(), any(), anyInt());
  }

  @Test
  void findRelevantFactsEmptyWhenEmbedCallFails() {
    ConversationMemoryService service = serviceWithStore();
    when(factEmbeddingService.isEnabled()).thenReturn(true);
    when(factEmbeddingService.embed("what do I like?")).thenReturn(null);

    assertEquals(List.of(), service.findRelevantFacts("user-1", "what do I like?"));
    verify(userFactRepository, never()).findMostSimilar(any(), any(), anyInt());
  }

  @Test
  void findRelevantFactsReturnsFactTextsInSimilarityOrder() {
    ConversationMemoryService service = serviceWithStore();
    float[] queryEmbedding = {0.1f, 0.2f};
    when(factEmbeddingService.isEnabled()).thenReturn(true);
    when(factEmbeddingService.embed("what do I like?")).thenReturn(queryEmbedding);
    UserFact f1 = new UserFact(1L, "user-1", "likes coffee", "conv-1", Instant.now(), Instant.now());
    UserFact f2 = new UserFact(2L, "user-1", "based in Amsterdam", "conv-1", Instant.now(), Instant.now());
    when(userFactRepository.findMostSimilar(eq("user-1"), any(), eq(5))).thenReturn(List.of(f1, f2));

    List<String> facts = service.findRelevantFacts("user-1", "what do I like?");

    assertEquals(List.of("likes coffee", "based in Amsterdam"), facts);
  }

  @Test
  void persistFactsEmbedsAndStoresEachSavedFact() {
    ConversationMemoryService service = serviceWithStore();
    List<ConversationTurn> turns = List.of(turn(), turn(), turn(), turn());
    when(store.load("conv-1")).thenReturn(new ConversationContext(null, turns, 950L));
    when(store.activeConversationCount("user-1")).thenReturn(1L);
    when(summarizer.summarize(any(), any())).thenReturn(new SummarizationResult("summary", List.of("likes coffee")));
    UserFact saved = new UserFact(7L, "user-1", "likes coffee", "conv-1", Instant.now(), Instant.now());
    when(userFactRepository.save(any())).thenReturn(saved);
    when(factEmbeddingService.isEnabled()).thenReturn(true);
    float[] embedding = {0.5f, 0.6f};
    when(factEmbeddingService.embed("likes coffee")).thenReturn(embedding);

    service.recordTurn("conv-1", "user-1", "hi", "hello");

    verify(userFactRepository).updateEmbedding(eq(7L), any());
  }

  @Test
  void persistFactsSkipsEmbeddingWriteWhenEmbedFails() {
    ConversationMemoryService service = serviceWithStore();
    List<ConversationTurn> turns = List.of(turn(), turn(), turn(), turn());
    when(store.load("conv-1")).thenReturn(new ConversationContext(null, turns, 950L));
    when(store.activeConversationCount("user-1")).thenReturn(1L);
    when(summarizer.summarize(any(), any())).thenReturn(new SummarizationResult("summary", List.of("likes coffee")));
    UserFact saved = new UserFact(7L, "user-1", "likes coffee", "conv-1", Instant.now(), Instant.now());
    when(userFactRepository.save(any())).thenReturn(saved);
    when(factEmbeddingService.isEnabled()).thenReturn(true);
    when(factEmbeddingService.embed("likes coffee")).thenReturn(null);

    assertDoesNotThrow(() -> service.recordTurn("conv-1", "user-1", "hi", "hello"));

    verify(userFactRepository, never()).updateEmbedding(any(), any());
  }

  private static ConversationTurn turn() {
    return new ConversationTurn(ConversationTurn.ROLE_USER, "message", Instant.now());
  }

}
