package com.mel.cb.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Proves ProviderRouter's two skip-without-calling paths against real HTTP: a provider that
 * answers 429 gets failed over to the next one, and a provider skipped for quota reasons is never
 * called at all. Uses real {@link OpenAiCompatibleProvider}s pointed at WireMock instead of mocked
 * {@link ChatProvider}s (that's {@link ProviderRouterTest}) so the failure actually travels
 * through the real OpenAI SDK HTTP client and exception types.
 */
class ProviderRouterFailoverWireMockTest {

  private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

  @RegisterExtension
  static WireMockExtension primaryServer = WireMockExtension.newInstance().build();

  @RegisterExtension
  static WireMockExtension secondaryServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    primaryServer.resetAll();
    secondaryServer.resetAll();
  }

  @Test
  void failsOverToSecondaryWhenPrimaryReturns429() {
    primaryServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
        .willReturn(aResponse().withStatus(429)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit_error\"}}")));
    secondaryServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
        .willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(chatCompletionBody("Hello from secondary"))));

    OpenAiCompatibleProvider primary = provider("primary", primaryServer.baseUrl(), 1, null);
    OpenAiCompatibleProvider secondary = provider("secondary", secondaryServer.baseUrl(), 2, null);
    ProviderRouter router = routerFor(List.of(primary, secondary), null);

    ChatResponse response = router.getReply(testPrompt());

    assertEquals("Hello from secondary", response.getResult().getOutput().getText());
    primaryServer.verify(1, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    secondaryServer.verify(1, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
  }

  @Test
  void quotaExhaustionSkipsProviderWithoutEverCallingIt() {
    secondaryServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
        .willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(chatCompletionBody("Hello from secondary"))));

    ProviderLimits limits = new ProviderLimits(1, 1000);
    OpenAiCompatibleProvider primary = provider("primary", primaryServer.baseUrl(), 1, limits);
    OpenAiCompatibleProvider secondary = provider("secondary", secondaryServer.baseUrl(), 2, null);
    QuotaTracker alwaysOverQuotaForPrimary = new QuotaTracker() {
      @Override
      public boolean isOverQuota(String providerId, ProviderLimits limits) {
        return "primary".equals(providerId);
      }

      @Override
      public void recordUsage(String providerId, long tokensUsed) {
      }
    };
    ProviderRouter router = routerFor(List.of(primary, secondary), alwaysOverQuotaForPrimary);

    ChatResponse response = router.getReply(testPrompt());

    assertEquals("Hello from secondary", response.getResult().getOutput().getText());
    primaryServer.verify(0, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    secondaryServer.verify(1, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
  }

  private static Prompt testPrompt() {
    return ChatPrompts.of("system", null, List.of(), "hi");
  }

  private static OpenAiCompatibleProvider provider(String id, String baseUrl, int priority, ProviderLimits limits) {
    ProviderProperties properties = new ProviderProperties();
    properties.setId(id);
    properties.setBaseUrl(baseUrl);
    properties.setModel("test-model");
    properties.setApiKeyEnv(id.toUpperCase() + "_API_KEY");
    properties.setPriority(priority);
    if (limits != null) {
      ProviderProperties.Limits configuredLimits = new ProviderProperties.Limits();
      configuredLimits.setRequestsPerDay(limits.requestsPerDay());
      configuredLimits.setTokensPerDay(limits.tokensPerDay());
      properties.setLimits(configuredLimits);
    }
    return new OpenAiCompatibleProvider(properties, "test-key");
  }

  @SuppressWarnings("unchecked")
  private static ProviderRouter routerFor(List<ChatProvider> providers, QuotaTracker quotaTracker) {
    ChatProviderRegistry registry = new ChatProviderRegistry(providers);

    ObjectProvider<QuotaTracker> quotaTrackerProvider = mock(ObjectProvider.class);
    when(quotaTrackerProvider.getIfAvailable()).thenReturn(quotaTracker);
    return new ProviderRouter(registry, quotaTrackerProvider);
  }

  private static String chatCompletionBody(String content) {
    return """
        {
          "id": "chatcmpl-test",
          "object": "chat.completion",
          "created": 1700000000,
          "model": "test-model",
          "choices": [
            {
              "index": 0,
              "message": {"role": "assistant", "content": "%s"},
              "finish_reason": "stop"
            }
          ],
          "usage": {"prompt_tokens": 5, "completion_tokens": 4, "total_tokens": 9}
        }
        """.formatted(content);
  }

}
