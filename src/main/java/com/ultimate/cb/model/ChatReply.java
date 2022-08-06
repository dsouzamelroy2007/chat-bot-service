package com.ultimate.cb.model;

import java.io.Serializable;
import java.time.Instant;
import javax.validation.constraints.NotNull;
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

}
