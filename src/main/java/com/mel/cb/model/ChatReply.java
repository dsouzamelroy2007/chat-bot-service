package com.mel.cb.model;

import java.io.Serializable;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatReply implements Serializable {

  @NotNull
  private String reply;

  private Instant timestamp;

  /** Echoes (or, for a new conversation, assigns) the id to pass back in on the next turn. */
  private String conversationId;

}
