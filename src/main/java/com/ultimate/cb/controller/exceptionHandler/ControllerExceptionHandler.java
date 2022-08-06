package com.ultimate.cb.controller.exceptionHandler;

import com.ultimate.cb.exception.IntentFetchException;
import com.ultimate.cb.exception.IntentParseException;
import com.ultimate.cb.exception.IntentSaveException;
import com.ultimate.cb.exception.InvalidInputException;
import com.ultimate.cb.model.ErrorDetails;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(IntentFetchException.class)
  public ResponseEntity processFetchException(IntentFetchException e, WebRequest request){
    ErrorDetails errorDetails = ErrorDetails.builder()
                                                .localDateTime(LocalDateTime.now())
                                                .message(e.getLocalizedMessage())
                                                .details(request.getDescription(false))
                                                .build();
    return new ResponseEntity(errorDetails,e. getHttpStatus());
  }

  @ExceptionHandler(IntentSaveException.class)
  public ResponseEntity processSaveException(IntentSaveException e, WebRequest request){
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


  @ExceptionHandler(InvalidInputException.class)
  public ResponseEntity processInvalidInputException(InvalidInputException e, WebRequest request){
    ErrorDetails errorDetails = ErrorDetails.builder()
                                            .localDateTime(LocalDateTime.now())
                                            .message(e.getLocalizedMessage())
                                            .details(request.getDescription(false))
                                            .build();
    return new ResponseEntity(errorDetails,e. getHttpStatus());
  }
}
