package com.mel.cb.service;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.util.ChatDataUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatReplyService {

  private final ProviderRouter providerRouter;

  @Value("${chatbot.system-prompt}")
  private String systemPrompt;

  public ChatReplyService(ProviderRouter providerRouter) {
    this.providerRouter = providerRouter;
  }

  public ChatReply getReplyForUserMessage(ChatMessage chatMessage){
    try {
      ChatResponse response = providerRouter.getReply(systemPrompt, chatMessage.getMessage());
      String replyText = response.getResult() != null ? response.getResult().getOutput().getText() : null;
      return ChatDataUtil.getChatReplyFromText(replyText);
    } catch (Exception e) {
      log.error("Exception while fetching AI reply for message {}", chatMessage, e);
      throw new AiReplyException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

}
