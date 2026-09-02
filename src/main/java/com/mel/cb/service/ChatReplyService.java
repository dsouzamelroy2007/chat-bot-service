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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
   * append.
   * <p>
   * <b>Real per-token streaming was re-confirmed live (docs/PLAN.md, post-Phase-6 follow-up) to
   * still work correctly through the Phase 6 {@code ChatClient}/{@code ToolCallingAdvisor} wiring</b>
   * -- decompiling {@code ChatClientMessageAggregator}/{@code MessageAggregator} showed they observe
   * each streamed chunk via {@code doOnNext}/{@code doOnComplete} side effects rather than buffering
   * the {@code Flux}, and a live test against Groq confirmed ~190 individual word-level SSE frames
   * arriving over several seconds, not one delayed blob. What actually regressed is narrower:
   * Gemini (priority 1) has a long, already-documented "thinking" delay before it emits any visible
   * text at all when tools are attached to the request (the same 38-83s figures recorded for the
   * non-streaming endpoint's tool round trips) -- a plain, non-tool streaming request can legitimately
   * produce zero output for tens of seconds before a burst of (possibly just one) chunk arrives, which
   * both looks like "streaming is broken" and can outrun this endpoint's own {@code SseEmitter}
   * timeout. Tool-calling support is kept on this endpoint rather than reverted to the raw
   * {@code ChatModel} -- reverting would sacrifice real capability to work around a provider latency
   * characteristic that doesn't actually require it.
   * <p>
   * A failure (during setup or mid-stream, including this endpoint's own {@link SseEmitter} timeout
   * firing) sends a named {@code error} event with a user-facing message, then completes the emitter
   * normally ({@link SseEmitter#complete()}, not {@code completeWithError}) -- {@code
   * completeWithError} hands the throwable back to Spring MVC as if it had escaped the controller
   * method, which re-runs it through the normal {@code @ControllerAdvice} exception-handling path
   * (here, {@code ControllerExceptionHandler}) that tries to write a second, JSON-shaped error body
   * onto a response that's already committed as an SSE stream -- caught during Phase 4's live
   * verification as a harmless-to-the-client but noisy "Response already committed. Ignoring" log
   * line. There's also no outer circuit-breaker fallback here the way there is for
   * {@code /chat/reply}, since wrapping a long-lived stream in the same {@code TimeLimiter} used for
   * the synchronous endpoint would be wrong for what can legitimately be a longer-running response.
   * <p>
   * {@code emitterFinished} guards against a real race found live (docs/PLAN.md, post-Phase-6
   * follow-up): when this endpoint's own {@link SseEmitter} timeout fires, Spring's default handling
   * completes the emitter internally with no callback of ours involved -- meanwhile this method's
   * worker thread is typically still blocked in {@code blockLast()} until the now-cancelled
   * underlying provider call actually throws, and only then reaches the {@code catch} block below.
   * By that point the emitter is already completed, so {@code emitter.send(...)} for the error event
   * throws {@code IllegalStateException} (not {@code IOException} -- {@link SseEmitter#send} declares
   * only the latter), which escaped uncaught on the executor thread before this fix. Registering an
   * explicit {@code onTimeout} handler here replaces Spring's silent default with the same
   * error-event-then-complete sequence used for every other failure, and {@code emitterFinished}
   * (checked via {@code compareAndSet}) ensures only whichever path -- the timeout callback or this
   * method's own catch block -- gets there first actually touches the emitter again.
   */
  public void streamReplyForUserMessage(ChatMessage chatMessage, SseEmitter emitter) {
    Instant startTime = Instant.now();
    String conversationId = resolveConversationId(chatMessage.getConversationId());
    StringBuilder fullText = new StringBuilder();
    AtomicBoolean emitterFinished = new AtomicBoolean(false);
    emitter.onTimeout(() -> {
      log.warn("Streaming timed out for message {}", chatMessage);
      completeWithError(emitter, emitterFinished);
    });
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
      if (emitterFinished.compareAndSet(false, true)) {
        emitter.complete();
      }
      log.info("chat_reply_stream userId={} conversationId={} status=200 durationMs={}",
          chatMessage.getUserId(), conversationId, Duration.between(startTime, Instant.now()).toMillis());
    } catch (Exception e) {
      log.error("Exception while streaming AI reply for message {}", chatMessage, e);
      completeWithError(emitter, emitterFinished);
    }
  }

  /**
   * Sends the {@code error} SSE event and completes the emitter, but only if neither this nor the
   * {@code onTimeout} callback in {@link #streamReplyForUserMessage} has already done so -- see that
   * method's doc for the race this guards against. Both {@link IOException} (the client connection
   * is already gone) and {@link IllegalStateException} (the emitter was already completed by
   * Spring's own async-timeout handling, or by the other racing path between the
   * {@code compareAndSet} above and this call) are swallowed as best-effort: either way, there's
   * nothing left to usefully do.
   */
  private void completeWithError(SseEmitter emitter, AtomicBoolean emitterFinished) {
    if (!emitterFinished.compareAndSet(false, true)) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().name("error").data(ChatConstants.NO_REPLY_AVAILABLE));
      emitter.complete();
    } catch (IOException | IllegalStateException ignored) {
      // best-effort -- see method doc
    }
  }

  private Prompt buildPrompt(ChatMessage chatMessage, ConversationContext context) {
    List<Message> history = context.turns().stream().map(ConversationTurn::toMessage).toList();
    List<String> facts = memoryService.findRelevantFacts(chatMessage.getUserId(), chatMessage.getMessage());
    return ChatPrompts.of(systemPrompt, context.summary(), facts, history, chatMessage.getMessage());
  }

  private static String extractText(ChatResponse response) {
    return response.getResult() != null ? response.getResult().getOutput().getText() : null;
  }

  private String resolveConversationId(String conversationId) {
    return conversationId != null && !conversationId.isBlank() ? conversationId : UUID.randomUUID().toString();
  }

}
