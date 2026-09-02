package com.mel.cb.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessLogFilterTest {

  private final AccessLogFilter filter = new AccessLogFilter();
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(AccessLogFilter.class)).addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    ((Logger) LoggerFactory.getLogger(AccessLogFilter.class)).detachAppender(appender);
  }

  @Test
  void logsMethodPathStatusAndDurationForASuccessfulRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bot/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(appender.list).hasSize(1);
    String message = appender.list.get(0).getFormattedMessage();
    assertThat(message).contains("method=GET", "path=/bot/actuator/health", "status=200", "durationMs=");
  }

  @Test
  void stillLogsWhenTheDownstreamChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bot/chat/reply");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain throwingChain = new MockFilterChain() {
      @Override
      public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
        throw new IllegalStateException("downstream failure");
      }
    };

    assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("method=POST", "path=/bot/chat/reply");
  }

}
