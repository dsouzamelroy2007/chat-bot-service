package com.mel.cb.provider;

import com.mel.cb.tools.ChatToolsRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
public class ProviderConfig {

  @Bean
  public List<ChatProvider> configuredChatProviders(ChatbotProviderProperties properties, ChatToolsRegistry toolsRegistry) {
    return properties.getProviders().stream()
        .<ChatProvider>map(p -> new OpenAiCompatibleProvider(p, System.getenv(p.getApiKeyEnv()), toolsRegistry.getToolCallbacks()))
        .toList();
  }

}
