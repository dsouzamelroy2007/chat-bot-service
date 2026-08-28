package com.mel.cb.provider;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sorts by priority the {@link ChatProvider}s Spring collects for this constructor -- both the
 * component-scanned ones (e.g. {@link StubChatProvider}) and the elements of the config-driven
 * {@code List<ChatProvider>} bean from {@link ProviderConfig}.
 * Spring merges both sources into a single {@code List<ChatProvider>} injection automatically, so
 * this only needs one constructor parameter.
 */
@Component
public class ChatProviderRegistry {

  private final List<ChatProvider> providers;

  public ChatProviderRegistry(List<ChatProvider> providers) {
    this.providers = providers.stream()
        .sorted(Comparator.comparingInt(ChatProvider::getPriority))
        .toList();
  }

  public List<ChatProvider> all() {
    return providers;
  }

}
