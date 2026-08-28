package com.mel.cb.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Tries {@link ChatProvider}s in priority order, skipping ones that are disabled, over their
 * daily quota, circuit-open, or rate-limited, and failing over to the next on any exception from
 * the call itself (429/5xx/timeout are the expected cases, but any other transport failure is
 * treated the same way -- there's no reason to give up on the whole request just because a
 * failure doesn't match one specific status code). Throws {@link ProvidersExhaustedException}
 * once every provider has been tried or skipped; callers let that -- like any other exception --
 * escape to the outer, request-level circuit breaker in ChatReplyController, which is what
 * actually produces the canned fallback reply (see docs/PLAN.md conflict #1).
 * <p>
 * Per-provider {@link CircuitBreaker} and {@link RateLimiter} instances are built here,
 * programmatically, keyed by provider id, rather than via Resilience4j annotations -- the
 * provider set is config-driven (@code chatbot.providers}), not a fixed list of named beans.
 */
@Slf4j
@Component
public class ProviderRouter {

  private final ChatProviderRegistry registry;
  private final QuotaTracker quotaTracker;
  private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  public ProviderRouter(ChatProviderRegistry registry, ObjectProvider<QuotaTracker> quotaTracker) {
    this.registry = registry;
    this.quotaTracker = quotaTracker.getIfAvailable();
    for (ChatProvider provider : registry.all()) {
      circuitBreakers.put(provider.getProviderId(), CircuitBreaker.of(provider.getProviderId(), circuitBreakerConfig()));
      rateLimiters.put(provider.getProviderId(), RateLimiter.of(provider.getProviderId(), rateLimiterConfig()));
    }
  }

  public ChatResponse getReply(Prompt prompt) {
    for (ChatProvider provider : registry.all()) {
      String id = provider.getProviderId();
      if (!provider.isEnabled()) {
        log.debug("Skipping provider {}: disabled", id);
        continue;
      }
      if (isOverQuota(provider)) {
        log.info("Skipping provider {}: at/above 90% of its daily quota", id);
        continue;
      }
      CircuitBreaker circuitBreaker = circuitBreakers.get(id);
      if (!circuitBreaker.tryAcquirePermission()) {
        log.info("Skipping provider {}: circuit breaker open", id);
        continue;
      }
      RateLimiter rateLimiter = rateLimiters.get(id);
      if (!rateLimiter.acquirePermission()) {
        circuitBreaker.releasePermission();
        log.info("Skipping provider {}: rate limit exceeded", id);
        continue;
      }
      long start = System.nanoTime();
      try {
        ChatResponse response = provider.reply(prompt);
        circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        recordUsage(provider, response);
        return response;
      } catch (Exception e) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
        log.warn("Provider {} failed, trying next provider: {}", id, e.getMessage());
      }
    }
    throw new ProvidersExhaustedException("All chat providers exhausted or unavailable");
  }

  /**
   * Streaming counterpart to {@link #getReply}, for the SSE endpoint (Phase 4). Applies the same
   * skip logic (disabled/over-quota/circuit-open/rate-limited) to pick the first viable provider,
   * but -- deliberately, unlike {@link #getReply} -- does <b>not</b> fail over to the next provider
   * if that one errors after streaming has already started: partial tokens may already be on their
   * way to the client, and retrying would mean replaying or duplicating output rather than cleanly
   * substituting a whole response the way failover does for the non-streaming path. A failure here
   * just ends the stream; the caller (ChatReplyService) turns that into a client-visible SSE error
   * event. Per-chunk token usage isn't reliably available mid-stream, so successful streams are
   * recorded to {@link QuotaTracker} as a request only (0 tokens) -- enough for the request-count-
   * metered free tiers (e.g. OpenRouter) to still see streaming traffic, without pretending to know
   * an exact token count.
   */
  public Flux<ChatResponse> streamReply(Prompt prompt) {
    for (ChatProvider provider : registry.all()) {
      String id = provider.getProviderId();
      if (!provider.isEnabled()) {
        log.debug("Skipping provider {}: disabled", id);
        continue;
      }
      if (isOverQuota(provider)) {
        log.info("Skipping provider {}: at/above 90% of its daily quota", id);
        continue;
      }
      CircuitBreaker circuitBreaker = circuitBreakers.get(id);
      if (!circuitBreaker.tryAcquirePermission()) {
        log.info("Skipping provider {}: circuit breaker open", id);
        continue;
      }
      RateLimiter rateLimiter = rateLimiters.get(id);
      if (!rateLimiter.acquirePermission()) {
        circuitBreaker.releasePermission();
        log.info("Skipping provider {}: rate limit exceeded", id);
        continue;
      }
      long start = System.nanoTime();
      return provider.streamReply(prompt)
          .doOnComplete(() -> {
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            if (provider.getLimits() != null && quotaTracker != null) {
              quotaTracker.recordUsage(id, 0);
            }
          })
          .doOnError(e -> {
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            log.warn("Provider {} failed while streaming: {}", id, e.getMessage());
          });
    }
    throw new ProvidersExhaustedException("All chat providers exhausted or unavailable");
  }

  private boolean isOverQuota(ChatProvider provider) {
    ProviderLimits limits = provider.getLimits();
    if (limits == null || quotaTracker == null) {
      return false;
    }
    return quotaTracker.isOverQuota(provider.getProviderId(), limits);
  }

  private void recordUsage(ChatProvider provider, ChatResponse response) {
    if (provider.getLimits() == null || quotaTracker == null) {
      return;
    }
    long tokens = 0;
    if (response.getMetadata() != null) {
      Usage usage = response.getMetadata().getUsage();
      if (usage != null && usage.getTotalTokens() != null) {
        tokens = usage.getTotalTokens();
      }
    }
    quotaTracker.recordUsage(provider.getProviderId(), tokens);
  }

  private static CircuitBreakerConfig circuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .slidingWindowSize(10)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .build();
  }

  private static RateLimiterConfig rateLimiterConfig() {
    return RateLimiterConfig.custom()
        .limitForPeriod(1)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build();
  }

}
