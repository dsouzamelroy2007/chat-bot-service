package com.mel.cb.model;

import java.io.Serializable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class ChatMessage implements Serializable {

  @NotNull
  String botId;

  @NotNull(message = "userId cannot be null")
  String userId;

  @NotNull(message = "Message cannot be null")
  String message;

  /** Continues an existing conversation; if null/blank a new one is started (see {@link com.mel.cb.model.ChatReply#getConversationId()}). */
  String conversationId;
}
