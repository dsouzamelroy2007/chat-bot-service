package com.mel.cb.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import com.mel.cb.provider.ProviderChatResponse;
import com.mel.cb.provider.ProviderRouter;

@ExtendWith(MockitoExtension.class)
class ConversationSummarizerTest {

  @Mock
  private ProviderRouter providerRouter;

  private ConversationSummarizer summarizer;

  private final List<ConversationTurn> turns = List.of(
      new ConversationTurn(ConversationTurn.ROLE_USER, "I love coffee", Instant.now()),
      new ConversationTurn(ConversationTurn.ROLE_ASSISTANT, "Noted!", Instant.now()));

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    summarizer = new ConversationSummarizer(providerRouter);
  }

  @Test
  void parsesWellFormedJsonResponse() {
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(
        responseWithText("{\"summary\":\"User discussed coffee preferences.\",\"facts\":[\"likes coffee\"]}"));

    SummarizationResult result = summarizer.summarize(null, turns);

    assertEquals("User discussed coffee preferences.", result.summary());
    assertEquals(List.of("likes coffee"), result.facts());
  }

  @Test
  void stripsMarkdownFencesBeforeParsing() {
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(
        responseWithText("```json\n{\"summary\":\"S\",\"facts\":[]}\n```"));

    SummarizationResult result = summarizer.summarize("old", turns);

    assertEquals("S", result.summary());
    assertTrue(result.facts().isEmpty());
  }

  @Test
  void fallsBackToExistingSummaryWithNoFactsOnMalformedJson() {
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(responseWithText("not json at all"));

    SummarizationResult result = summarizer.summarize("existing summary", turns);

    assertEquals("existing summary", result.summary());
    assertTrue(result.facts().isEmpty());
  }

  @Test
  void fallsBackToExistingSummaryWhenProviderRouterThrows() {
    when(providerRouter.getReply(any(Prompt.class))).thenThrow(new RuntimeException("all providers exhausted"));

    SummarizationResult result = summarizer.summarize("existing summary", turns);

    assertEquals("existing summary", result.summary());
    assertTrue(result.facts().isEmpty());
  }

  private static ProviderChatResponse responseWithText(String text) {
    ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    return new ProviderChatResponse(response, "primary", 0L);
  }

}
