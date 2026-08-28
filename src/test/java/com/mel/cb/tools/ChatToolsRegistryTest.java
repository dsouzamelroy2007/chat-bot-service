package com.mel.cb.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;

@ExtendWith(MockitoExtension.class)
class ChatToolsRegistryTest {

  @Mock
  private ChatTool disabledTool;

  @Test
  void onlyEnabledToolsContributeCallbacks() {
    when(disabledTool.isEnabled()).thenReturn(false);
    EnabledTool enabledTool = new EnabledTool();

    ChatToolsRegistry registry = new ChatToolsRegistry(List.of(disabledTool, enabledTool));

    assertEquals(1, registry.getToolCallbacks().size());
    assertTrue(registry.getToolCallbacks().get(0).getToolDefinition().name().contains("doThing"));
  }

  @Test
  void emptyWhenNothingEnabled() {
    when(disabledTool.isEnabled()).thenReturn(false);

    ChatToolsRegistry registry = new ChatToolsRegistry(List.of(disabledTool));

    assertTrue(registry.getToolCallbacks().isEmpty());
  }

  public static class EnabledTool implements ChatTool {
    @Override
    public boolean isEnabled() {
      return true;
    }

    @Tool(description = "does a thing")
    public String doThing() {
      return "done";
    }
  }

}
