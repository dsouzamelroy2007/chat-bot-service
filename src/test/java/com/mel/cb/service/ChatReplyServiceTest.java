package com.mel.cb.service;

import static com.mel.cb.util.MockDataCreator.getChatMessageForTest;
import static com.mel.cb.util.MockDataCreator.getChatReplyForTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.memory.ConversationContext;
import com.mel.cb.memory.ConversationMemoryService;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.provider.ProvidersExhaustedException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class ChatReplyServiceTest {

  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

  private ChatReplyService chatReplyService;

  @Mock
  private ProviderRouter providerRouter;

  @Mock
  private ConversationMemoryService memoryService;

  @Mock
  private SseEmitter emitter;

  private ChatReply chatReply;

  private ChatMessage chatMessage;


  @BeforeEach
  public void setUp(){
    chatReplyService = new ChatReplyService(providerRouter, memoryService);
    ReflectionTestUtils.setField(chatReplyService, "systemPrompt", SYSTEM_PROMPT);

    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
    when(memoryService.loadContext(anyString())).thenReturn(ConversationContext.empty());
  }

  @Test
  public void testGetReplyForUserMessageFail() {
    when(providerRouter.getReply(any(Prompt.class)))
        .thenThrow(new ProvidersExhaustedException("All chat providers exhausted or unavailable"));

    Assertions.assertThrows(AiReplyException.class, () -> {
      chatReplyService.getReplyForUserMessage(chatMessage);
    });
  }

  @Test
  public void testGetReplyForUserMessageSuccess() {
    ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(chatReply.getReply()))));
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(response);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);

    Assertions.assertEquals(chatReply.getReply(), actualReply.getReply());
    Assertions.assertNotNull(actualReply.getConversationId());
    verify(memoryService).recordTurn(actualReply.getConversationId(), chatMessage.getUserId(),
        chatMessage.getMessage(), actualReply.getReply());
  }

  @Test
  public void testGetReplyForUserMessageIncludesRetrievedFactsInThePrompt() {
    when(memoryService.findRelevantFacts(chatMessage.getUserId(), chatMessage.getMessage()))
        .thenReturn(List.of("likes coffee", "based in Amsterdam"));
    ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(chatReply.getReply()))));
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(response);

    chatReplyService.getReplyForUserMessage(chatMessage);

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(providerRouter).getReply(promptCaptor.capture());
    String allMessageText = promptCaptor.getValue().getInstructions().stream()
        .map(m -> m.getText())
        .reduce("", String::concat);
    Assertions.assertTrue(allMessageText.contains("likes coffee"));
    Assertions.assertTrue(allMessageText.contains("based in Amsterdam"));
  }

  @Test
  public void testGetReplyForUserMessageEmptyResponse() {
    ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(response);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);
    Assertions.assertEquals(com.mel.cb.constants.ChatConstants.NO_REPLY_AVAILABLE, actualReply.getReply());
  }

  @Test
  public void testGetReplyForUserMessageReusesGivenConversationId() {
    chatMessage.setConversationId("conversation-42");
    ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(chatReply.getReply()))));
    when(providerRouter.getReply(any(Prompt.class))).thenReturn(response);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);

    Assertions.assertEquals("conversation-42", actualReply.getConversationId());
  }

  @Test
  public void testStreamReplyForUserMessageAccumulatesChunksAndRecordsFullText() throws Exception {
    when(providerRouter.streamReply(any(Prompt.class)))
        .thenReturn(Flux.just(chunk("Hello"), chunk(" world")));

    chatReplyService.streamReplyForUserMessage(chatMessage, emitter);

    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
    verify(memoryService).recordTurn(anyString(), eq(chatMessage.getUserId()), eq(chatMessage.getMessage()), eq("Hello world"));
  }

  @Test
  public void testStreamReplyForUserMessageReusesGivenConversationId() throws Exception {
    chatMessage.setConversationId("conversation-42");
    when(providerRouter.streamReply(any(Prompt.class))).thenReturn(Flux.just(chunk("hi")));

    chatReplyService.streamReplyForUserMessage(chatMessage, emitter);

    verify(memoryService).recordTurn(eq("conversation-42"), any(), any(), any());
  }

  @Test
  public void testStreamReplyForUserMessageProvidersExhaustedSendsErrorEventAndCompletesNormally() throws Exception {
    when(providerRouter.streamReply(any(Prompt.class)))
        .thenThrow(new ProvidersExhaustedException("All chat providers exhausted or unavailable"));

    chatReplyService.streamReplyForUserMessage(chatMessage, emitter);

    // complete(), not completeWithError() -- the client already got a friendly `error` SSE event;
    // completeWithError() would additionally re-run the exception through Spring's normal
    // @ControllerAdvice handling, which tries (and fails, harmlessly but noisily) to write a
    // second JSON error body onto an already-committed SSE response. See ChatReplyService javadoc.
    verify(emitter).complete();
    verify(emitter, never()).completeWithError(any());
    verify(memoryService, never()).recordTurn(any(), any(), any(), any());
  }

  @Test
  public void testStreamReplyForUserMessageMidStreamErrorSendsErrorEventAndCompletesNormally() throws Exception {
    when(providerRouter.streamReply(any(Prompt.class)))
        .thenReturn(Flux.concat(Flux.just(chunk("partial")), Flux.error(new RuntimeException("boom"))));

    chatReplyService.streamReplyForUserMessage(chatMessage, emitter);

    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
    verify(emitter, never()).completeWithError(any());
    verify(memoryService, never()).recordTurn(any(), any(), any(), any());
  }

  private static ChatResponse chunk(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

}
