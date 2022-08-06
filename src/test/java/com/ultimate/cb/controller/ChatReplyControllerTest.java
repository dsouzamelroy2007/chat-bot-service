package com.ultimate.cb.controller;

import static com.ultimate.cb.util.ChatDataUtil.getObjectAsString;
import static com.ultimate.cb.util.ChatDataUtil.readFileData;
import static com.ultimate.cb.util.MockDataCreator.getChatMessageForTest;
import static com.ultimate.cb.util.MockDataCreator.getChatReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentToSaveForTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.exception.IntentFetchException;
import com.ultimate.cb.exception.IntentSaveException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.service.ChatReplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest
@ContextConfiguration(classes = ChatReplyController.class)
@AutoConfigureMockMvc
public class ChatReplyControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockBean
  private ChatReplyService chatReplyService;

  private ChatReply chatReply;

  private ChatMessage chatMessage;

  private IntentReply intentReply;

  private IntentModel intentToSave;


  @BeforeEach
  public void setUp(){
    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
    intentReply = getIntentReplyForTest();
    intentToSave = getIntentToSaveForTest();
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
                .thenThrow(new IntentFetchException("Dummy exception"));

    this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(chatMessage)))
        .andExpect(status().isInternalServerError())
        .andReturn();
  }


  @Test
  public void testGetChatReplyisOk() throws Exception {
    when(chatReplyService.getReplyForUserMessage(any(ChatMessage.class)))
        .thenReturn(chatReply);

    this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(chatMessage)))
        .andExpect(status().isOk())
        .andReturn();

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

    this.mockMvc.perform(post("/chat/reply")
        .contentType(MediaType.APPLICATION_JSON)
        .content(readFileData("ChatMessageValidInput.json")))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void testSaveChatReplyis404Error() throws Exception{
    when(chatReplyService.saveIntent(any(IntentModel.class)))
        .thenReturn(intentReply);

    this.mockMvc.perform(post("/chat/intent/save1111")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(intentToSave)))
        .andExpect(status().is4xxClientError())
        .andReturn();
  }

  @Test
  public void testSaveChatReplyisInternalServer() throws Exception{
    when(chatReplyService.saveIntent(any(IntentModel.class)))
        .thenThrow(new IntentSaveException("Dummy exception"));

    this.mockMvc.perform(post("/chat/intent/save")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(intentToSave)))
        .andExpect(status().isInternalServerError())
        .andReturn();
  }

  @Test
  public void testSaveChatReplyisSuccess() throws Exception{
    when(chatReplyService.saveIntent(any(IntentModel.class)))
        .thenReturn(intentReply);

    this.mockMvc.perform(post("/chat/intent/save")
        .contentType(MediaType.APPLICATION_JSON)
        .content(getObjectAsString(intentToSave)))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void testSaveChatReplyisBadRequest() throws Exception{

    this.mockMvc.perform(post("/chat/intent/save")
        .contentType(MediaType.APPLICATION_JSON)
        .content(readFileData("IntentInvalidInput.json")))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  public void testSaveChatReplyisValidRequest() throws Exception{
    when(chatReplyService.saveIntent(any(IntentModel.class)))
        .thenReturn(intentReply);

    this.mockMvc.perform(post("/chat/intent/save")
        .contentType(MediaType.APPLICATION_JSON)
        .content(readFileData("IntentValidInput.json")))
        .andExpect(status().isOk())
        .andReturn();
  }

}
