package com.mel.cb.tools;

/**
 * Marker implemented by every tool bean whose {@code @Tool}-annotated methods
 * {@link ChatToolsRegistry} may expose to the model. {@link #isEnabled()} lets a tool that needs an
 * API key opt itself out of that set at runtime -- the same disable-if-unconfigured convention
 * {@code com.mel.cb.provider.ChatProvider} already uses for LLM providers -- rather than the model
 * being offered a tool that would just fail if it tried to call it.
 */
public interface ChatTool {

  boolean isEnabled();

}
