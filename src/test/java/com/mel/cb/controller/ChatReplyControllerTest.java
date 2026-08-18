package com.mel.cb.controller;

import static com.mel.cb.util.ChatDataUtil.getObjectAsString;
import static com.mel.cb.util.ChatDataUtil.readFileData;
import static com.mel.cb.util.MockDataCreator.getChatMessageForTest;
import static com.mel.cb.util.MockDataCreator.getChatReplyForTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mel.cb.exception.AiReplyException;
import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import com.mel.cb.service.ChatReplyService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(SpringExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {ChatReplyController.class, ChatReplyControllerTest.TestExecutorConfig.class})
@AutoConfigureMockMvc
public class ChatReplyControllerTest {

  @TestConfiguration
  static class TestExecutorConfig {
    @Bean("threadPoolTaskExecutor")
    Executor threadPoolTaskExecutor() {
      return Executors.newSingleThreadExecutor();
    }
  }

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  private ChatReplyService chatReplyService;

  private ChatReply chatReply;

  private ChatMessage chatMessage;


  @BeforeEach
  public void setUp(){
    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
  }

  @Test
  public void testGetChatReplyis404() throws Exception{
    when(chatReplyService.getReplyForUserMessage(any(ChatMessage.class)))
                .thenReturn(chatReply);

    this.mockMvc.perform(post("/chat/replyyyy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(getObjectAsString(chatMessage)))
                .andExpect(status().is4xxClientError())
                .andReturn();
  }

  @Test
  public void testGetChatReplyis500() throws Exception{
    when(chatReplyService.getReplyForUserMessage(any(ChatMessage.class)))
                .thenThrow(new AiReplyException("Dummy exception"));

    MvcResult result = this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(chatMessage)))
        .andExpect(request().asyncStarted())
        .andReturn();

    this.mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isInternalServerError());
  }


  @Test
  public void testGetChatReplyisOk() throws Exception {
    when(chatReplyService.getReplyForUserMessage(any(ChatMessage.class)))
        .thenReturn(chatReply);

    MvcResult result = this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(chatMessage)))
        .andExpect(request().asyncStarted())
        .andReturn();

    this.mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetChatReplyisBadRequest() throws Exception {

    this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(readFileData("ChatMessageInvalidInput.json")))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  public void testGetChatReplyisValidRequest() throws Exception {
    when(chatReplyService.getReplyForUserMessage(any(ChatMessage.class)))
        .thenReturn(chatReply);

    MvcResult result = this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(readFileData("ChatMessageValidInput.json")))
        .andExpect(request().asyncStarted())
        .andReturn();

    this.mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk());
  }

}
