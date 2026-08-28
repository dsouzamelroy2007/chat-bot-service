package com.mel.cb.security;

import com.mel.cb.model.ErrorDetails;
import com.mel.cb.util.ChatDataUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Shared JSON-error-body writer for the security filters -- they run ahead of the
 * {@code DispatcherServlet}, so {@link com.mel.cb.controller.exceptionHandler.ControllerExceptionHandler}
 * never sees a rejection made here; each filter has to write its own response body directly. */
final class SecurityResponses {

  private SecurityResponses() {
  }

  static void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    ErrorDetails errorDetails = ErrorDetails.builder()
        .localDateTime(LocalDateTime.now())
        .message(message)
        .details(request.getRequestURI())
        .build();
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(ChatDataUtil.getObjectAsString(errorDetails));
  }

}
