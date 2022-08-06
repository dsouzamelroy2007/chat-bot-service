package com.ultimate.cb.controller;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.service.ChatReplyService;
import com.ultimate.cb.util.ChatDataUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.time.Duration;
import java.time.Instant;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@Slf4j
@Api(value = "REST APIs that are used by chat bots to provide replies to user messages" )
public class ChatReplyController {
  

  @Autowired
  private ChatReplyService chatReplyService;

  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success|OK"),
      @ApiResponse(code = 401, message = "not authorized!"),
      @ApiResponse(code = 403, message = "forbidden!!!"),
      @ApiResponse(code = 404, message = "not found!!!"),
      @ApiResponse(code = 500, message = "Internal Server Error!!!")})
  @ApiOperation(value = "Give Reply to User based on the generated intent for the given user message", tags = "replyToUser")
  @HystrixCommand(fallbackMethod = "fallback_replyToUser_AI_API_TIMEOUT",
      commandProperties = {
          @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "20000")
      }
      )
  @RequestMapping(method = RequestMethod.POST, value = "/reply")
  public ResponseEntity<ChatReply> replyToUser(@Valid @RequestBody ChatMessage chatMessage) {
    Instant startTime = Instant.now();
    try {
      ChatReply response = chatReplyService.getReplyForUserMessage(chatMessage);
      return new ResponseEntity(response, HttpStatus.OK);
    } finally {
      log.info("RequestType: {}, Response_Code: {}, Timestamp: {} ms", "chat_message_reply", HttpStatus.OK,
          Duration
              .between(startTime, Instant.now())
              .toMillis());
    }
  }



  public ResponseEntity<ChatReply> fallback_replyToUser_AI_API_TIMEOUT(ChatMessage chatMessage){
    ChatReply chatReply = ChatDataUtil.getChatReplyFromIntent(null);
    return new ResponseEntity(chatReply, HttpStatus.BAD_GATEWAY);
  }

  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success|OK"),
      @ApiResponse(code = 401, message = "not authorized!"),
      @ApiResponse(code = 403, message = "forbidden!!!"),
      @ApiResponse(code = 404, message = "not found!!!"),
      @ApiResponse(code = 500, message = "Internal Server Error!!!")})
  @ApiOperation(value = "Save a new intent or update an existing intent with new confidence or reply", tags = "replyToUser")
  @RequestMapping(method = RequestMethod.POST, value = "/intent/save")
  public ResponseEntity saveIntentAndReply(@Valid @RequestBody IntentModel intentModel){
    Instant startTime = Instant.now();
    try {
      IntentReply response = chatReplyService.saveIntent(intentModel);
      return new ResponseEntity(response, HttpStatus.OK);
    } finally {
      log.info("RequestType: {}, Response_Code: {}, Timestamp: {} ms", "save_intent_and_reply", HttpStatus.OK,
          Duration
              .between(startTime, Instant.now())
              .toMillis());
    }
  }

}
