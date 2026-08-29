package com.mel.cb.provider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

/**
 * A {@link ChatProvider} backed by any OpenAI-compatible chat completions API (Gemini, Groq,
 * Cerebras, Mistral, OpenRouter, ...). The {@link OpenAiChatModel} is built programmatically from
 * the {@code chatbot.providers} config entry rather than via Spring AI's OpenAI autoconfiguration
 * -- that autoconfiguration only supports a single, statically-configured client and eagerly
 * builds unrelated embedding/image/moderation/audio clients that fail without a global
 * {@code OPENAI_API_KEY} (see docs/PLAN.md, Phase 0 notes).
 * <p>
 * Tool callbacks (Phase 3, {@code com.mel.cb.tools.ChatToolsRegistry}) are baked into this
 * model's own default {@link OpenAiChatOptions} here at construction, not attached per-request via
 * {@code Prompt}'s runtime options -- confirmed against the actual {@code OpenAiChatModel} source
 * that {@code buildRequestPrompt} only falls back to the model's defaults when
 * {@code prompt.getOptions() == null}; any non-null runtime options object is used as-is with no
 * merging; and {@code internalCall} then hard-casts it to {@code OpenAiChatOptions}. A per-request
 * options object built by a caller with no idea of this provider's own model/baseUrl/apiKey would
 * silently discard them all rather than merge, breaking the request -- see docs/PLAN.md for the
 * real (live-tested, not {@code javap}-assumed) failure this replaced.
 * <p>
 * Calls go through a {@link ChatClient} wrapping the {@link OpenAiChatModel}, not the model
 * directly -- a second real (live-tested) surprise, found during Phase 6 deployment testing
 * (docs/PLAN.md): {@code OpenAiChatModel.call(Prompt)}/{@code stream(Prompt)} build the request and
 * hand back whatever the API returns, including a bare {@code tool_calls} response with no text,
 * with no loop of their own -- confirmed by decompiling the resolved 2.0.0 jar and finding no
 * {@code executeToolCalls} call anywhere in the class. The automatic "call the tool, feed the
 * result back, get a real answer" loop this project's earlier phases assumed came from the model
 * itself is actually a {@code ChatClient} advisor ({@code ToolCallingAdvisor}, registered by
 * default by {@code ChatClient.builder(ChatModel)} with no extra configuration needed) -- going
 * through a model directly, as every phase through Phase 5 did, silently skips it. Tool callbacks
 * stay baked into the model's own default {@link OpenAiChatOptions} as before; the {@code Prompt}
 * passed to {@code ChatClient.prompt(Prompt)} still carries no options of its own
 * ({@code ChatPrompts.of} never sets any), so the same {@code prompt.getOptions() == null} fallback
 * the earlier {@code ClassCastException} fix relies on is unaffected by this change.
 */
@Slf4j
public class OpenAiCompatibleProvider implements ChatProvider {

  private final String providerId;
  private final int priority;
  private final String baseUrl;
  private final String apiKey;
  private final ProviderLimits limits;
  private final ChatClient chatClient;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public OpenAiCompatibleProvider(ProviderProperties properties, String apiKey, List<ToolCallback> toolCallbacks) {
    this.providerId = properties.getId();
    this.priority = properties.getPriority();
    this.baseUrl = properties.getBaseUrl();
    this.apiKey = apiKey != null ? apiKey : "";
    this.limits = properties.getLimits() != null
        ? new ProviderLimits(properties.getLimits().getRequestsPerDay(), properties.getLimits().getTokensPerDay())
        : null;
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
            .baseUrl(this.baseUrl)
            .apiKey(this.apiKey)
            .model(properties.getModel())
            // ProviderRouter already fails over to the next provider on any error, including
            // 429/5xx -- letting the OpenAI SDK's own default (3 retries, with backoff) run first
            // would keep hammering an already-rate-limited provider instead of moving on.
            .maxRetries(0)
            // Overrides AbstractOpenAiOptions.DEFAULT_TIMEOUT (60s) -- a real, independent call
            // timeout on the underlying OpenAI-compatible HTTP client itself, found live (docs/
            // PLAN.md, streaming-regression follow-up) by decompiling AbstractOpenAiOptions after a
            // long Gemini tool-calling stream kept getting cut off at ~60s regardless of this
            // service's own SseEmitter/resilience4j timeouts. It's unrelated to and sits underneath
            // both of those -- raising them alone (as Phase 6 already did for resilience4j's
            // chatReply TimeLimiter, still 60s) can't help while this default silently caps every
            // provider call at 60s first, before either of those ever gets a chance to fire.
            // Deliberately kept below ChatReplyController.STREAM_TIMEOUT_MS (90s), not equal to it --
            // an equal-value first attempt at this fix (docs/PLAN.md) produced an exact tie live,
            // which let the SseEmitter's own container-level timeout handling win the race instead of
            // this app's own catch block on roughly half of runs, silently ending the stream with no
            // `error` event rather than the intended clean one. 80s gives this timeout -- and this
            // app's own exception handling in ChatReplyService, which runs off the back of it -- room
            // to reliably fire first; the sync endpoint's 60s chatReply TimeLimiter already fires
            // well before either value, unaffected by this change.
            .timeout(Duration.ofSeconds(80))
            .toolCallbacks(toolCallbacks != null ? toolCallbacks : List.of())
            .build())
        .build();
    this.chatClient = ChatClient.builder(chatModel).build();
    if (this.apiKey.isBlank()) {
      disable("no API key configured (env var " + properties.getApiKeyEnv() + " not set)");
    }
  }

  @Override
  public String getProviderId() {
    return providerId;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public boolean isEnabled() {
    return enabled.get();
  }

  @Override
  public void disable(String reason) {
    if (enabled.compareAndSet(true, false)) {
      log.warn("Provider {} disabled: {}", providerId, reason);
    }
  }

  @Override
  public ProviderLimits getLimits() {
    return limits;
  }

  @Override
  public ChatResponse reply(Prompt prompt) {
    return chatClient.prompt(prompt).call().chatResponse();
  }

  @Override
  public Flux<ChatResponse> streamReply(Prompt prompt) {
    return chatClient.prompt(prompt).stream().chatResponse();
  }

  @Override
  public boolean checkHealth(RestClient restClient) {
    try {
      restClient.get()
          .uri(baseUrl + "/models")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (Exception e) {
      disable("startup health check against " + baseUrl + "/models failed: " + e.getMessage());
      return false;
    }
  }

}
