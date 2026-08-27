package com.mel.cb.provider;

import lombok.Data;

/** One entry of the {@code chatbot.providers} config list. */
@Data
public class ProviderProperties {

  private String id;
  private String baseUrl;
  private String model;
  private String apiKeyEnv;
  private int priority;
  private Limits limits;

  @Data
  public static class Limits {
    private long requestsPerDay;
    private long tokensPerDay;
  }

}
