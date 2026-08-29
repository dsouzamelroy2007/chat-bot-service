package com.mel.cb.controller;

import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.service.ChatReplyService;
import com.mel.cb.util.ChatDataUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
@Slf4j
@Tag(name = "Chat Reply", description = "REST APIs that are used by chat bots to provide replies to user messages")
public class ChatReplyController {

  /** Generous but bounded -- independent of the sync endpoint's 60s resilience4j TimeLimiter, which
   * would be wrong for a response that can legitimately take longer to fully stream out. Raised
   * from 60s (docs/PLAN.md, post-Phase-6 follow-up) after live testing found Gemini (priority 1) can
   * take up to the same 38-83s documented for the non-streaming endpoint's tool round trips just to
   * emit its first visible token when tools are attached, which a 60s stream timeout could not
   * reliably outlast; kept below {@code spring.mvc.async.request-timeout} (application.yml) so this
   * timeout -- and its clean {@code error} SSE event -- fires before the container's own. Still
   * doesn't guarantee covering the worst observed case, same accepted tradeoff as the sync path. */
  private static final long STREAM_TIMEOUT_MS = 90_000L;

  @Autowired
  private ChatReplyService chatReplyService;

  @Autowired
  @Qualifier("threadPoolTaskExecutor")
  private Executor chatReplyExecutor;

  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success|OK"),
      @ApiResponse(responseCode = "401", description = "not authorized!"),
      @ApiResponse(responseCode = "403", description = "forbidden!!!"),
      @ApiResponse(responseCode = "404", description = "not found!!!"),
      @ApiResponse(responseCode = "500", description = "Internal Server Error!!!")})
  @Operation(summary = "Give an AI-generated reply to the user's message")
  @CircuitBreaker(name = "chatReply", fallbackMethod = "fallback_replyToUser_AI_API_TIMEOUT")
  @TimeLimiter(name = "chatReply")
  @RequestMapping(method = RequestMethod.POST, value = "/reply")
  public CompletableFuture<ResponseEntity<ChatReply>> replyToUser(@Valid @RequestBody ChatMessage chatMessage) {
    return CompletableFuture.supplyAsync(() -> {
      Instant startTime = Instant.now();
      try {
        ChatReply response = chatReplyService.getReplyForUserMessage(chatMessage);
        return new ResponseEntity<>(response, HttpStatus.OK);
      } finally {
        log.info("RequestType: {}, Response_Code: {}, Timestamp: {} ms", "chat_message_reply", HttpStatus.OK,
            Duration
                .between(startTime, Instant.now())
                .toMillis());
      }
    }, chatReplyExecutor);
  }



  public CompletableFuture<ResponseEntity<ChatReply>> fallback_replyToUser_AI_API_TIMEOUT(ChatMessage chatMessage, Throwable throwable){
    ChatReply chatReply = ChatDataUtil.getChatReplyFromText(null);
    chatReply.setConversationId(chatMessage.getConversationId());
    return CompletableFuture.completedFuture(new ResponseEntity<>(chatReply, HttpStatus.BAD_GATEWAY));
  }

  @Operation(summary = "Stream an AI-generated reply to the user's message via Server-Sent Events")
  @RequestMapping(method = RequestMethod.POST, value = "/reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamReplyToUser(@Valid @RequestBody ChatMessage chatMessage) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    chatReplyExecutor.execute(() -> chatReplyService.streamReplyForUserMessage(chatMessage, emitter));
    return emitter;
  }

}
