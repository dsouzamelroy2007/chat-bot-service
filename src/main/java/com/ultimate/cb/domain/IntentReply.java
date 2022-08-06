package com.ultimate.cb.domain;


import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "intentReplies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntentReply {

  @Id
  private String id;

  @NotNull
  private String intent;

  private String description;

  @NotNull
  private String confidenceThreshold;

  @NotNull
  private String reply;
}
