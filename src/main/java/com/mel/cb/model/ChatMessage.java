package com.mel.cb.model;

import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class ChatMessage implements Serializable {

  @NotBlank(message = "botId cannot be blank")
  String botId;

  @NotBlank(message = "userId cannot be blank")
  String userId;

  /**
   * Capped at 4000 chars (Phase 5, docs/PLAN.md) -- generous for one chat turn, but bounded so a
   * single message can't eat most of a user's per-conversation token budget
   * ({@code chatbot.memory.user-token-budget}) or blow past a provider's context window by itself.
   */
  @NotBlank(message = "Message cannot be blank")
  @Size(max = 4000, message = "Message cannot exceed 4000 characters")
  String message;

  /** Continues an existing conversation; if null/blank a new one is started (see {@link com.mel.cb.model.ChatReply#getConversationId()}). */
  String conversationId;
}
