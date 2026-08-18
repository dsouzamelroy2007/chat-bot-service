package com.mel.cb.service;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.util.ChatDataUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatReplyService {

  private final ChatClient chatClient;

  @Value("${chatbot.system-prompt}")
  private String systemPrompt;

  public ChatReplyService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  public ChatReply getReplyForUserMessage(ChatMessage chatMessage){
    try {
      String replyText = chatClient.prompt()
          .system(systemPrompt)
          .user(chatMessage.getMessage())
          .call()
          .content();
      return ChatDataUtil.getChatReplyFromText(replyText);
    } catch (Exception e) {
      log.error("Exception while fetching AI reply for message {}", chatMessage, e);
      throw new AiReplyException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

}
