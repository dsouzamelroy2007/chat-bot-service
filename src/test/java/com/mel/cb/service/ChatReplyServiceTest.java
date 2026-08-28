package com.mel.cb.service;

import static com.mel.cb.util.MockDataCreator.getChatMessageForTest;
import static com.mel.cb.util.MockDataCreator.getChatReplyForTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.memory.ConversationContext;
import com.mel.cb.memory.ConversationMemoryService;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.provider.ProvidersExhaustedException;
import com.mel.cb.tools.ChatToolsRegistry;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ChatReplyServiceTest {

  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

  private ChatReplyService chatReplyService;

  @Mock
  private ProviderRouter providerRouter;

  @Mock
  private ConversationMemoryService memoryService;

  @Mock
  private ChatToolsRegistry toolsRegistry;

  private ChatReply chatReply;

  private ChatMessage chatMessage;


  @BeforeEach
  public void setUp(){
    chatReplyService = new ChatReplyService(providerRouter, memoryService, toolsRegistry);
    ReflectionTestUtils.setField(chatReplyService, "systemPrompt", SYSTEM_PROMPT);

    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
    when(memoryService.loadContext(anyString())).thenReturn(ConversationContext.empty());
    when(toolsRegistry.getToolCallbacks()).thenReturn(List.of());
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

}
