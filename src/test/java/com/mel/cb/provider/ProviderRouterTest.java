package com.mel.cb.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.beans.factory.ObjectProvider;

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
    when(primary.reply(anyString(), anyString())).thenThrow(new RuntimeException("simulated 429"));
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(anyString(), anyString())).thenReturn(expected);

    ChatResponse actual = router.getReply("sys", "hi");

    assertSame(expected, actual);
    verify(primary).reply("sys", "hi");
    verify(secondary).reply("sys", "hi");
  }

  @Test
  void skipsDisabledProviderWithoutCallingIt() {
    when(primary.isEnabled()).thenReturn(false);
    when(secondary.isEnabled()).thenReturn(true);
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(anyString(), anyString())).thenReturn(expected);

    ChatResponse actual = router.getReply("sys", "hi");

    assertSame(expected, actual);
    verify(primary, never()).reply(anyString(), anyString());
  }

  @Test
  void skipsProviderOverQuotaWithoutCallingIt() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    ChatResponse expected = chatResponse("from secondary");
    when(secondary.reply(anyString(), anyString())).thenReturn(expected);

    ChatResponse actual = router.getReply("sys", "hi");

    assertSame(expected, actual);
    verify(primary, never()).reply(anyString(), anyString());
  }

  @Test
  void throwsProvidersExhaustedWhenEveryProviderFails() {
    when(primary.isEnabled()).thenReturn(true);
    when(secondary.isEnabled()).thenReturn(true);
    when(primary.reply(anyString(), anyString())).thenThrow(new RuntimeException("boom1"));
    when(secondary.reply(anyString(), anyString())).thenThrow(new RuntimeException("boom2"));

    assertThrows(ProvidersExhaustedException.class, () -> router.getReply("sys", "hi"));
  }

  @Test
  void recordsTokenUsageOnSuccessWhenLimitsPresent() {
    ProviderLimits limits = new ProviderLimits(100, 1000);
    when(primary.isEnabled()).thenReturn(true);
    when(primary.getLimits()).thenReturn(limits);
    when(quotaTracker.isOverQuota("primary", limits)).thenReturn(false);
    when(primary.reply(anyString(), anyString())).thenReturn(chatResponseWithUsage("ok", 42));

    router.getReply("sys", "hi");

    verify(quotaTracker).recordUsage("primary", 42L);
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
