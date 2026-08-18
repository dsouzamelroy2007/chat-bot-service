package com.mel.cb.controller.exceptionHandler;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.exception.IntentParseException;
import com.mel.cb.model.ErrorDetails;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(AiReplyException.class)
  public ResponseEntity processFetchException(AiReplyException e, WebRequest request){
    ErrorDetails errorDetails = ErrorDetails.builder()
                                                .localDateTime(LocalDateTime.now())
                                                .message(e.getLocalizedMessage())
                                                .details(request.getDescription(false))
                                                .build();
    return new ResponseEntity(errorDetails,e. getHttpStatus());
  }

  @ExceptionHandler(IntentParseException.class)
  public ResponseEntity processParseException(IntentParseException e, WebRequest request){
    ErrorDetails errorDetails = ErrorDetails.builder()
                                              .localDateTime(LocalDateTime.now())
                                              .message(e.getLocalizedMessage())
                                              .details(request.getDescription(false))
                                              .build();
    return new ResponseEntity(errorDetails,e. getHttpStatus());
  }
}
