package com.mel.cb.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProviderRouterTest {

  @Mock
  private ChatProvider primary;

  @Mock
  private ChatProvider secondary;

  @Mock
  private QuotaTracker quotaTracker;

  @Mock
  private ObjectProvider<QuotaTracker> quotaTrackerProvider;

  private ProviderRouter router;

  private final Prompt prompt = ChatPrompts.of("sys", null, List.of(), List.of(), "hi");

  @BeforeEach
  void setUp() {
    when(primary.getProviderId()).thenReturn("primary");
    when(primary.getPriority()).thenReturn(1);
    when(secondary.getProviderId()).thenReturn("secondary");
    when(secondary.getPriority()).thenReturn(2);

    ChatProviderRegistry registry = new ChatProviderRegistry(List.of(primary, secondary));
    when(quotaTrackerProvider.getIfAvailable()).thenReturn(quotaTracker);
    router = new ProviderRouter(registry, quotaTrackerProvider);
  }

  @Test
  void failsOverToNextProviderWhenFirstThrows() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.reply(any(Prompt.class))).thenThrow(new RuntimeException("simulated 429"));
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(any(Prompt.class))).thenReturn(expected);

    ChatResponse actual = router.getReply(prompt);

    assertSame(expected, actual);
    verify(primary).reply(prompt);
    verify(secondary).reply(prompt);
  }

  @Test
  void skipsDisabledProviderWithoutCallingIt() {
    when(primary.isEnabled()).thenReturn(false);
    when(secondary.isEnabled()).thenReturn(true);
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(any(Prompt.class))).thenReturn(expected);

    ChatResponse actual = router.getReply(prompt);

    assertSame(expected, actual);
    verify(primary, never()).reply(any(Prompt.class));
  }

  @Test
  void skipsProviderOverQuotaWithoutCallingIt() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(any(Prompt.class))).thenReturn(expected);

    ChatResponse actual = router.getReply(prompt);

    assertSame(expected, actual);
    verify(primary, never()).reply(any(Prompt.class));
  }

  @Test
  void failsOverToNextProviderWhenFirstReturnsBlankReply() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.reply(any(Prompt.class))).thenReturn(chatResponse(""));
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(any(Prompt.class))).thenReturn(expected);

    ChatResponse actual = router.getReply(prompt);

    assertSame(expected, actual);
    verify(primary).reply(prompt);
    verify(secondary).reply(prompt);
  }

  @Test
  void recordsUsageForAProviderThatReturnedABlankReply() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(false);
    when(primary.reply(any(Prompt.class))).thenReturn(chatResponseWithUsage("", 17));
    when(secondary.isEnabled()).thenReturn(true);
    when(secondary.reply(any(Prompt.class))).thenReturn(chatResponse("from secondary"));

    router.getReply(prompt);

    verify(quotaTracker).recordUsage("primary", 17L);
  }

  @Test
  void throwsProvidersExhaustedWhenEveryProviderReturnsBlankReplies() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.reply(any(Prompt.class))).thenReturn(chatResponse(""));
    when(secondary.reply(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

    assertThrows(ProvidersExhaustedException.class, () -> router.getReply(prompt));
  }

  @Test
  void throwsProvidersExhaustedWhenEveryProviderFails() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.reply(any(Prompt.class))).thenThrow(new RuntimeException("boom1"));
    when(secondary.reply(any(Prompt.class))).thenThrow(new RuntimeException("boom2"));

    assertThrows(ProvidersExhaustedException.class, () -> router.getReply(prompt));
  }

  @Test
  void recordsTokenUsageOnSuccessWhenLimitsPresent() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(false);
    when(primary.reply(any(Prompt.class))).thenReturn(chatResponseWithUsage("ok", 42));

    router.getReply(prompt);

    verify(quotaTracker).recordUsage("primary", 42L);
  }

  @Test
  void streamPicksFirstViableProviderWithoutFailover() {
    when(primary.isEnabled()).thenReturn(true);
    ChatResponse chunk = chatResponse("hello");
    when(primary.streamReply(any(Prompt.class))).thenReturn(Flux.just(chunk));

    Flux<ChatResponse> result = router.streamReply(prompt);

    StepVerifier.create(result).expectNext(chunk).verifyComplete();
    verify(secondary, never()).streamReply(any(Prompt.class));
  }

  @Test
  void streamSkipsDisabledProviderWithoutCallingIt() {
    when(primary.isEnabled()).thenReturn(false);
    when(secondary.isEnabled()).thenReturn(true);
    ChatResponse chunk = chatResponse("hello from secondary");
    when(secondary.streamReply(any(Prompt.class))).thenReturn(Flux.just(chunk));

    Flux<ChatResponse> result = router.streamReply(prompt);

    StepVerifier.create(result).expectNext(chunk).verifyComplete();
    verify(primary, never()).streamReply(any(Prompt.class));
  }

  @Test
  void streamDoesNotFailOverWhenTheChosenProviderErrorsMidStream() {
    when(primary.isEnabled()).thenReturn(true);
    RuntimeException failure = new RuntimeException("connection reset mid-stream");
    when(primary.streamReply(any(Prompt.class))).thenReturn(Flux.concat(Flux.just(chatResponse("partial")), Flux.error(failure)));

    Flux<ChatResponse> result = router.streamReply(prompt);

    StepVerifier.create(result).expectNextCount(1).verifyErrorMatches(e -> e == failure);
    verify(secondary, never()).streamReply(any(Prompt.class));
  }

  @Test
  void streamFailsOverToNextProviderWhenFirstErrorsBeforeEmittingAnything() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    RuntimeException failure = new RuntimeException("simulated connect timeout");
    when(primary.streamReply(any(Prompt.class))).thenReturn(Flux.error(failure));
    ChatResponse chunk = chatResponse("from secondary");
    when(secondary.streamReply(any(Prompt.class))).thenReturn(Flux.just(chunk));

    Flux<ChatResponse> result = router.streamReply(prompt);

    StepVerifier.create(result).expectNext(chunk).verifyComplete();
    verify(primary).streamReply(prompt);
    verify(secondary).streamReply(prompt);
  }

  @Test
  void streamThrowsProvidersExhaustedWhenEveryProviderErrorsBeforeEmittingAnything() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.streamReply(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom1")));
    when(secondary.streamReply(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom2")));

    Flux<ChatResponse> result = router.streamReply(prompt);

    StepVerifier.create(result).verifyError(ProvidersExhaustedException.class);
  }

  @Test
  void streamThrowsProvidersExhaustedWhenNoProviderIsViable() {
    when(primary.isEnabled()).thenReturn(false);
    when(secondary.isEnabled()).thenReturn(false);

    assertThrows(ProvidersExhaustedException.class, () -> router.streamReply(prompt));
  }

  @Test
  void streamRecordsRequestOnlyUsageOnCompletionWhenLimitsPresent() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(false);
    when(primary.streamReply(any(Prompt.class))).thenReturn(Flux.just(chatResponse("ok")));

    StepVerifier.create(router.streamReply(prompt)).expectNextCount(1).verifyComplete();

    verify(quotaTracker).recordUsage("primary", 0L);
  }

  private static ChatResponse chatResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static ChatResponse chatResponseWithUsage(String text, int totalTokens) {
    return ChatResponse.builder()
        .generations(List.of(new Generation(new AssistantMessage(text))))
        .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(0, totalTokens)).build())
        .build();
  }

}
