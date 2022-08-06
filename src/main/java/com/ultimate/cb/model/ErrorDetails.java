package com.ultimate.cb.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class ErrorDetails implements Serializable {
  private LocalDateTime localDateTime;
  private String message;
  private String details;

}
