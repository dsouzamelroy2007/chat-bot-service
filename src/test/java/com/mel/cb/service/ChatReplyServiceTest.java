package com.mel.cb.service;

import static com.mel.cb.util.MockDataCreator.getChatMessageForTest;
import static com.mel.cb.util.MockDataCreator.getChatReplyForTest;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ChatReplyServiceTest {

  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

  private ChatReplyService chatReplyService;

  @Mock
  private ChatClient.Builder chatClientBuilder;

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClient.ChatClientRequestSpec requestSpec;

  @Mock
  private ChatClient.CallResponseSpec callResponseSpec;

  private ChatReply chatReply;

  private ChatMessage chatMessage;


  @BeforeEach
  public void setUp(){
    when(chatClientBuilder.build()).thenReturn(chatClient);
    chatReplyService = new ChatReplyService(chatClientBuilder);
    ReflectionTestUtils.setField(chatReplyService, "systemPrompt", SYSTEM_PROMPT);

    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
  }

  @Test
  public void testGetReplyForUserMessageFail() {
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenThrow(new RuntimeException("GatewayTime Error"));

    Assertions.assertThrows(AiReplyException.class, () -> {
      chatReplyService.getReplyForUserMessage(chatMessage);
    });
  }

  @Test
  public void testGetReplyForUserMessageSuccess() {
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.content()).thenReturn(chatReply.getReply());

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);
    Assertions.assertEquals(chatReply.getReply(), actualReply.getReply());
  }

  @Test
  public void testGetReplyForUserMessageEmptyResponse() {
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.content()).thenReturn(null);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);
    Assertions.assertEquals(com.mel.cb.constants.ChatConstants.NO_REPLY_AVAILABLE, actualReply.getReply());
  }

}
