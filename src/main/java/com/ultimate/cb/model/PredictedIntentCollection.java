package com.ultimate.cb.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PredictedIntentCollection {

  List<PredictedIntent> intents;
  List<Object> entities;
}
