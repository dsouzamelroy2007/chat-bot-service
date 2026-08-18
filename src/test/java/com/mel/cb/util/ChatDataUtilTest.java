package com.mel.cb.util;

import static com.mel.cb.util.ChatDataUtil.fromJson;
import static com.mel.cb.util.ChatDataUtil.getChatReplyFromText;
import static com.mel.cb.util.ChatDataUtil.getObjectAsString;
import static com.mel.cb.util.MockDataCreator.getChatMessageForTest;
import static com.mel.cb.util.MockDataCreator.getChatReplyForTest;

import com.mel.cb.constants.ChatConstants;
import com.mel.cb.exception.IntentParseException;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChatDataUtilTest {
  private ChatReply chatReply;

  private ChatMessage chatMessage;

  @BeforeEach
  public void setUp(){
    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
  }

  @Test
  public void testFromJsonFailure(){
    Assertions.assertThrows(IntentParseException.class, () -> {
      fromJson("not valid json", ChatMessage.class);
    });
  }

  @Test
  public void testToJsonAndBackRoundTrip(){
    String json = getObjectAsString(chatMessage);
    ChatMessage roundTripped = fromJson(json, ChatMessage.class);
    Assertions.assertEquals(chatMessage.getMessage(), roundTripped.getMessage());
  }

  @Test
  public void testGetDefaultChatReplyFromText(){
    ChatReply actualReply = getChatReplyFromText(null);
    Assertions.assertEquals(ChatConstants.NO_REPLY_AVAILABLE, actualReply.getReply());
  }

  @Test
  public void testGetChatReplyFromText(){
    ChatReply actualReply = getChatReplyFromText(chatReply.getReply());
    Assertions.assertEquals(chatReply.getReply(), actualReply.getReply());
  }
}
