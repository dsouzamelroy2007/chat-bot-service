package com.mel.cb.service;

import com.mel.cb.constants.ChatConstants;
import com.mel.cb.exception.AiReplyException;
import com.mel.cb.memory.ConversationContext;
import com.mel.cb.memory.ConversationMemoryService;
import com.mel.cb.memory.ConversationTurn;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.provider.ChatPrompts;
import com.mel.cb.provider.ProviderRouter;
import com.mel.cb.util.ChatDataUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class ChatReplyService {

  private final ProviderRouter providerRouter;
  private final ConversationMemoryService memoryService;

  @Value("${chatbot.system-prompt}")
  private String systemPrompt;

  public ChatReplyService(ProviderRouter providerRouter, ConversationMemoryService memoryService) {
    this.providerRouter = providerRouter;
    this.memoryService = memoryService;
  }

  public ChatReply getReplyForUserMessage(ChatMessage chatMessage){
    String conversationId = resolveConversationId(chatMessage.getConversationId());
    try {
      Prompt prompt = buildPrompt(chatMessage, memoryService.loadContext(conversationId));

      ChatResponse response = providerRouter.getReply(prompt);
      ChatReply reply = ChatDataUtil.getChatReplyFromText(extractText(response));
      reply.setConversationId(conversationId);

      memoryService.recordTurn(conversationId, chatMessage.getUserId(), chatMessage.getMessage(), reply.getReply());
      return reply;
    } catch (Exception e) {
      log.error("Exception while fetching AI reply for message {}", chatMessage, e);
      throw new AiReplyException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  /**
   * Streaming counterpart (Phase 4), driven off {@link ProviderRouter#streamReply}. Runs on the
   * caller's thread -- the controller dispatches this onto {@code chatReplyExecutor}, the same
   * background pool the non-streaming endpoint already uses, so blocking here (the Flux is
   * consumed synchronously via {@code doOnNext}/{@code blockLast}, and {@code memoryService}'s
   * Redis/Postgres calls are themselves blocking) is safe -- it's off the servlet request thread
   * either way, matching this codebase's existing non-reactive style rather than introducing
   * Reactor {@code Schedulers} for a single feature.
   * <p>
   * The conversation id is sent as the first SSE event (named {@code conversation}) rather than a
   * response header, since {@link SseEmitter} doesn't expose response headers to a controller
   * returning it directly, and a value inside the stream itself survives any intermediary the same
   * way the rest of the stream does. Each subsequent default-named event is one text delta to
   * append. A failure (during setup or mid-stream) sends a named {@code error} event with a
   * user-facing message, then completes the emitter normally ({@link SseEmitter#complete()}, not
   * {@code completeWithError}) -- {@code completeWithError} hands the throwable back to Spring MVC
   * as if it had escaped the controller method, which re-runs it through the normal
   * {@code @ControllerAdvice} exception-handling path (here, {@code ControllerExceptionHandler})
   * that tries to write a second, JSON-shaped error body onto a response that's already committed
   * as an SSE stream -- caught during live verification as a harmless-to-the-client but noisy
   * "Response already committed. Ignoring" log line. The client has already been told about the
   * failure via the {@code error} event by this point; there's nothing left for Spring's own
   * exception handling to usefully do. There's also no outer circuit-breaker fallback here the way
   * there is for {@code /chat/reply}, since wrapping a long-lived stream in the same 20s
   * {@code TimeLimiter} used for the synchronous endpoint would be wrong for what can legitimately
   * be a longer-running response.
   */
  public void streamReplyForUserMessage(ChatMessage chatMessage, SseEmitter emitter) {
    String conversationId = resolveConversationId(chatMessage.getConversationId());
    StringBuilder fullText = new StringBuilder();
    try {
      Prompt prompt = buildPrompt(chatMessage, memoryService.loadContext(conversationId));
      emitter.send(SseEmitter.event().name("conversation").data(conversationId));

      providerRouter.streamReply(prompt)
          .doOnNext(response -> {
            String delta = extractText(response);
            if (delta != null && !delta.isEmpty()) {
              fullText.append(delta);
              try {
                emitter.send(SseEmitter.event().data(delta));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            }
          })
          .blockLast();

      String finalReply = fullText.isEmpty() ? ChatConstants.NO_REPLY_AVAILABLE : fullText.toString();
      memoryService.recordTurn(conversationId, chatMessage.getUserId(), chatMessage.getMessage(), finalReply);
      emitter.complete();
    } catch (Exception e) {
      log.error("Exception while streaming AI reply for message {}", chatMessage, e);
      try {
        emitter.send(SseEmitter.event().name("error").data(ChatConstants.NO_REPLY_AVAILABLE));
      } catch (IOException ignored) {
        // best-effort -- the client connection may already be gone
      }
      emitter.complete();
    }
  }

  private Prompt buildPrompt(ChatMessage chatMessage, ConversationContext context) {
    List<Message> history = context.turns().stream().map(ConversationTurn::toMessage).toList();
    return ChatPrompts.of(systemPrompt, context.summary(), history, chatMessage.getMessage());
  }

  private static String extractText(ChatResponse response) {
    return response.getResult() != null ? response.getResult().getOutput().getText() : null;
  }

  private String resolveConversationId(String conversationId) {
    return conversationId != null && !conversationId.isBlank() ? conversationId : UUID.randomUUID().toString();
  }

}
