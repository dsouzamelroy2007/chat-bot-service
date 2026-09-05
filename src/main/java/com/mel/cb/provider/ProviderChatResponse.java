package com.mel.cb.provider;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * A {@link ChatResponse} tagged with which {@link ChatProvider} produced it and how long that
 * took, so callers can surface provider attribution
 * without re-deriving it themselves. {@code latencyMs} means the full round-trip for
 * {@link ProviderRouter#getReply}, but only "elapsed since this provider's stream started" for
 * each {@link ProviderRouter#streamReply} chunk -- callers that want time-to-first-token should
 * read it off the first chunk only.
 */
public record ProviderChatResponse(ChatResponse response, String providerId, long latencyMs) {

}
