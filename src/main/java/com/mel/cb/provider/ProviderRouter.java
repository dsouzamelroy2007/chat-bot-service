package com.mel.cb.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * failure doesn't match one specific status code) -- {@link #getReply} additionally fails over on
 * a response that threw nothing but came back with no usable text at all (see its own doc for the
 * concrete provider quirk this was added for, Phase 6, docs/PLAN.md). Throws {@link ProvidersExhaustedException}
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

  public ProviderChatResponse getReply(Prompt prompt) {
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
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        recordUsage(provider, response);
        if (isBlankReply(response)) {
          // The provider genuinely consumed quota/tokens (recorded above) and threw nothing, but
          // returned unusable output -- e.g. Gemini's OpenAI-compatible endpoint 400ing on the
          // tool-result follow-up call for lacking a Gemini-specific `thought_signature` that
          // Spring AI's generic tool-calling loop doesn't know to echo back, discovered live
          // during Phase 6 deployment testing (docs/PLAN.md). Treated the same as a thrown
          // exception for failover purposes: a 200 with nothing useful in it is not meaningfully
          // different from a failure from the caller's perspective, and gating failover on
          // exceptions alone left this kind of silent, provider-specific quirk with no way to
          // ever reach the next provider.
          circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS,
              new IllegalStateException("Provider " + id + " returned an empty reply"));
          log.warn("Provider {} returned an empty reply after {} ms, trying next provider", id, elapsedMs);
          continue;
        }
        circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        log.info("Provider {} responded in {} ms", id, elapsedMs);
        return new ProviderChatResponse(response, id, elapsedMs);
      } catch (Exception e) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
        log.warn("Provider {} failed, trying next provider: {}", id, e.getMessage());
      }
    }
    throw new ProvidersExhaustedException("All chat providers exhausted or unavailable");
  }

  /**
   * A provider can return a normal, exception-free {@link ChatResponse} that still has nothing
   * usable in it (see the {@link #getReply} call site for the concrete case this was written for).
   * Mirrors {@code ChatReplyService.extractText}'s null-safety rather than sharing it directly --
   * that method is private to a different package and this needs only a yes/no check, not the
   * extracted text itself.
   */
  private static boolean isBlankReply(ChatResponse response) {
    if (response.getResult() == null) {
      return true;
    }
    String text = response.getResult().getOutput().getText();
    return text == null || text.isBlank();
  }

  /**
   * Streaming counterpart to {@link #getReply}, for the SSE endpoint (Phase 4). Applies the same
   * skip logic (disabled/over-quota/circuit-open/rate-limited) as {@link #getReply}, and -- as of
   * the post-Phase-6 live-deployment follow-up (docs/PLAN.md) -- also fails over to the next
   * provider if the chosen one errors <b>before emitting any chunk at all</b> (its own connect/read
   * timeout, an immediate 4xx/5xx, etc.). This was added after a live Render deployment showed a
   * plain, tool-free streaming request could still time out entirely against a single flaky
   * priority-1 provider with no fallback, unlike {@link #getReply} which already fails past the
   * same kind of failure. Once at least one chunk has reached the caller, though, this deliberately
   * still does <b>not</b> fail over on a later error: partial tokens are already on their way to the
   * client, and retrying would mean replaying or duplicating output rather than cleanly substituting
   * a whole response the way failover does before anything has been sent. Either way, a failure that
   * exhausts every remaining provider (or arrives after output has started) just ends the stream; the
   * caller ({@code ChatReplyService}) turns that into a client-visible SSE error event.
   * <p>
   * The "has anything been emitted yet" check is a plain {@link AtomicBoolean} flipped from
   * {@code doOnNext}, not a buffering/peek-first-element approach -- this keeps genuine chunks
   * streaming to the client the moment they arrive, rather than holding the first one back to decide
   * whether a retry might still be possible.
   * <p>
   * Per-chunk token usage isn't reliably available mid-stream, so successful streams are recorded to
   * {@link QuotaTracker} as a request only (0 tokens) -- enough for the request-count-metered free
   * tiers (e.g. OpenRouter) to still see streaming traffic, without pretending to know an exact token
   * count.
   * <p>
   * Does <b>not</b> get {@link #getReply}'s empty-but-200 failover (Phase 6, docs/PLAN.md) -- doing so
   * would mean buffering an unknown number of chunks before deciding the whole stream was empty and
   * only then trying the next provider, which conflicts with actually streaming as chunks arrive. A
   * provider with the same quirk that caused that fix would show up here as a stream that opens (so
   * the pre-first-chunk failover above doesn't apply) and then emits nothing, rather than a clean
   * fallback.
   */
  public Flux<ProviderChatResponse> streamReply(Prompt prompt) {
    return streamReplyFrom(prompt, registry.all(), 0);
  }

  private Flux<ProviderChatResponse> streamReplyFrom(Prompt prompt, List<ChatProvider> providers, int index) {
    if (index >= providers.size()) {
      throw new ProvidersExhaustedException("All chat providers exhausted or unavailable");
    }
    ChatProvider provider = providers.get(index);
    String id = provider.getProviderId();
    if (!provider.isEnabled()) {
      log.debug("Skipping provider {}: disabled", id);
      return streamReplyFrom(prompt, providers, index + 1);
    }
    if (isOverQuota(provider)) {
      log.info("Skipping provider {}: at/above 90% of its daily quota", id);
      return streamReplyFrom(prompt, providers, index + 1);
    }
    CircuitBreaker circuitBreaker = circuitBreakers.get(id);
    if (!circuitBreaker.tryAcquirePermission()) {
      log.info("Skipping provider {}: circuit breaker open", id);
      return streamReplyFrom(prompt, providers, index + 1);
    }
    RateLimiter rateLimiter = rateLimiters.get(id);
    if (!rateLimiter.acquirePermission()) {
      circuitBreaker.releasePermission();
      log.info("Skipping provider {}: rate limit exceeded", id);
      return streamReplyFrom(prompt, providers, index + 1);
    }

    long start = System.nanoTime();
    AtomicBoolean emittedAny = new AtomicBoolean(false);
    return provider.streamReply(prompt)
        // Elapsed-since-attempt-start on every chunk, not just the first -- cheap to compute, and
        // it's the caller's job (ChatReplyService) to only care about the first chunk's value as a
        // time-to-first-token figure for the widget.
        .map(response -> new ProviderChatResponse(response, id, Duration.ofNanos(System.nanoTime() - start).toMillis()))
        .doOnNext(chunk -> emittedAny.set(true))
        .doOnComplete(() -> {
          circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
          if (provider.getLimits() != null && quotaTracker != null) {
            quotaTracker.recordUsage(id, 0);
          }
        })
        .onErrorResume(e -> {
          circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
          if (emittedAny.get()) {
            log.warn("Provider {} failed mid-stream, ending stream (output already sent): {}", id, e.getMessage());
            return Flux.error(e);
          }
          log.warn("Provider {} failed before emitting any output, trying next provider: {}", id, e.getMessage());
          return streamReplyFrom(prompt, providers, index + 1);
        });
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
