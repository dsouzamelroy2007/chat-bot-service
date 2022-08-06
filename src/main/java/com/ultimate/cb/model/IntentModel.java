package com.ultimate.cb.model;

import java.io.Serializable;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class IntentModel implements Serializable {

  @NotNull(message = "Intent cannot be null")
  private String intent;

  private String description;

  @NotNull(message = "confidenceThreshold cannot be null")
  private String confidenceThreshold;

  private String reply;
}
