package com.mel.cb.provider;

/** Thrown by {@link ProviderRouter} when every configured provider was disabled, over quota, circuit-open, rate-limited, or failed. */
public class ProvidersExhaustedException extends RuntimeException {

  public ProvidersExhaustedException(String message) {
    super(message);
  }

}
