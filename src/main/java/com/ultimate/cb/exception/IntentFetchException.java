package com.ultimate.cb.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class IntentFetchException extends RuntimeException{
  private static final long serialVersionUID = -14819139347930533L;
  private HttpStatus httpStatus;
  private Map<String, String> body;

  public IntentFetchException(HttpStatus httpStatus,Throwable cause) {
    super(cause);
    this.httpStatus = httpStatus;
    this.body = new HashMap<>();
    this.body.put("msg", cause != null ? cause.getMessage() : "Resource is null");
  }

  public IntentFetchException(HttpStatus httpStatus, String message){
    super(message);
    this.body = new HashMap<>();
    this.body.put("msg", message);
    this.httpStatus = httpStatus;
  }

  public IntentFetchException(String message){
    super(message);
    this.body = new HashMap<>();
    this.body.put("msg", message);
    this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
  }

  public ResponseEntity getResponse(){
    return ResponseEntity.status(this.httpStatus).body(this.body);
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String toString() {
    return getResponse() + " " + getMessage();
  }
}
