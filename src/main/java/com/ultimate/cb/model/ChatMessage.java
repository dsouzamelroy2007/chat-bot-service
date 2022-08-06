package com.ultimate.cb.model;

import java.io.Serializable;
import javax.validation.constraints.NotNull;
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

  @NotNull(message = "Message cannot be null")
  String message;
}
