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

  /** Id of the {@code ChatProvider} that actually answered, or {@code null} for the outer
   * circuit-breaker's canned fallback (no provider ever replied). */
  private String provider;

  /** Time the winning provider call took, in milliseconds -- round-trip for {@code /chat/reply},
   * time-to-first-token for {@code /chat/reply/stream}. {@code null} alongside {@link #provider}. */
  private Long latencyMs;

}
