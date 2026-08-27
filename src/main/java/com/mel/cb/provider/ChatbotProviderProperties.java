package com.mel.cb.provider;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chatbot")
public class ChatbotProviderProperties {

  private List<ProviderProperties> providers = new ArrayList<>();

}
