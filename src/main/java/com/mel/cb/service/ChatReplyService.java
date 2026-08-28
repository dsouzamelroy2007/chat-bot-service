package com.mel.cb.service;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.memory.ConversationContext;
import com.mel.cb.memory.ConversationMemoryService;
import com.mel.cb.memory.ConversationTurn;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.provider.ChatPrompts;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.util.ChatDataUtil;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatReplyService {

  private final ProviderRouter providerRouter;
  private final ConversationMemoryService memoryService;

  @Value("${chatbot.system-prompt}")
  private String systemPrompt;

  public ChatReplyService(ProviderRouter providerRouter, ConversationMemoryService memoryService) {
    this.providerRouter = providerRouter;
    this.memoryService = memoryService;
  }

  public ChatReply getReplyForUserMessage(ChatMessage chatMessage){
    String conversationId = resolveConversationId(chatMessage.getConversationId());
    try {
      ConversationContext context = memoryService.loadContext(conversationId);
      List<Message> history = context.turns().stream().map(ConversationTurn::toMessage).toList();
      Prompt prompt = ChatPrompts.of(systemPrompt, context.summary(), history, chatMessage.getMessage());

      ChatResponse response = providerRouter.getReply(prompt);
      String replyText = response.getResult() != null ? response.getResult().getOutput().getText() : null;
      ChatReply reply = ChatDataUtil.getChatReplyFromText(replyText);
      reply.setConversationId(conversationId);

      memoryService.recordTurn(conversationId, chatMessage.getUserId(), chatMessage.getMessage(), reply.getReply());
      return reply;
    } catch (Exception e) {
      log.error("Exception while fetching AI reply for message {}", chatMessage, e);
      throw new AiReplyException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private String resolveConversationId(String conversationId) {
    return conversationId != null && !conversationId.isBlank() ? conversationId : UUID.randomUUID().toString();
  }

}
