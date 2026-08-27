package com.mel.cb.provider;

/** Daily per-provider request/token usage, backed by Redis in {@link RedisQuotaTracker}. */
public interface QuotaTracker {

  /** True once the provider is at or above 90% of either its daily request or token cap. */
  boolean isOverQuota(String providerId, ProviderLimits limits);

  void recordUsage(String providerId, long tokensUsed);

}
