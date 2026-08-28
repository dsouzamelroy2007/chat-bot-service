package com.mel.cb.tools;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ToolCallback} list {@code ChatReplyService} attaches to every prompt, from
 * whichever {@link ChatTool} beans are actually {@link ChatTool#isEnabled() enabled} -- a tool
 * whose API key isn't configured is left out entirely rather than offered to the model and then
 * failing if called, the same "skip rather than offer-and-fail" principle
 * {@code com.mel.cb.provider.ProviderRouter} already applies to disabled LLM providers.
 */
@Component
public class ChatToolsRegistry {

  private final List<ToolCallback> toolCallbacks;

  public ChatToolsRegistry(List<ChatTool> tools) {
    Object[] enabledTools = tools.stream().filter(ChatTool::isEnabled).toArray();
    this.toolCallbacks = enabledTools.length == 0
        ? List.of()
        : List.of(MethodToolCallbackProvider.builder().toolObjects(enabledTools).build().getToolCallbacks());
  }

  public List<ToolCallback> getToolCallbacks() {
    return toolCallbacks;
  }

}
