package com.ultimate.cb.service;

import static com.ultimate.cb.util.ChatDataUtil.fromJson;
import static com.ultimate.cb.util.ChatDataUtil.readFileData;
import static com.ultimate.cb.util.MockDataCreator.getChatMessageForTest;
import static com.ultimate.cb.util.MockDataCreator.getChatReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentToSaveForTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ultimate.cb.constants.ChatConstants;
import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.exception.IntentFetchException;
import com.ultimate.cb.exception.IntentSaveException;
import com.ultimate.cb.exception.InvalidInputException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.model.PredictedIntentCollection;
import com.ultimate.cb.repository.IntentReplyRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
public class ChatReplyServiceTest {

  @InjectMocks
  private ChatReplyService chatReplyService;

  @Mock
  private IntentReplyRepository intentReplyRepository;

  @Mock
  private RestService restService;

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
  public void testGetReplyForUserMessageFail() {
    Throwable throwable = new Throwable("GatewayTime Error");
    Exception exception = new Exception(throwable);
    when(restService.post(anyString(), any(ChatMessage.class), any(Class.class)))
        .thenThrow(new IntentFetchException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getCause()));
    Assertions.assertThrows(IntentFetchException.class, () -> {
      chatReplyService.getReplyForUserMessage(chatMessage);
    });

  }

  @Test
  public void testGetReplyForUserMessageSuccessAboveThreshold() {
    String intentsJsonStr = readFileData("test_Intents.json");
    PredictedIntentCollection predictedIntentCollection = fromJson(intentsJsonStr, PredictedIntentCollection.class);

    when(restService.post(anyString(), any(ChatMessage.class), any(Class.class)))
        .thenReturn(predictedIntentCollection);
    when(intentReplyRepository.findByIntentAndConfidenceThresholdLessThanEqual(any(String.class), any(BigDecimal.class)))
        .thenReturn(intentReply);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);
    Assertions.assertEquals(chatReply.getReply(), actualReply.getReply());
  }

  @Test
  public void testGetReplyForUserMessageSuccessBelowThreshold() {
    String intentsJsonStr = readFileData("test_Intents.json");
    PredictedIntentCollection predictedIntentCollection = fromJson(intentsJsonStr, PredictedIntentCollection.class);
    when(restService.post(anyString(), any(ChatMessage.class), any(Class.class)))
        .thenReturn(predictedIntentCollection);

    when(intentReplyRepository.findByIntentAndConfidenceThresholdLessThanEqual(any(String.class), any(BigDecimal.class)))
        .thenReturn(null);

    ChatReply actualReply = chatReplyService.getReplyForUserMessage(chatMessage);
    Assertions.assertEquals(ChatConstants.NO_REPLY_AVAILABLE, actualReply.getReply());
  }

  @Test
  public void testSaveIntentFail(){
    Throwable throwable = new Throwable("DB connection timeout");
    Exception exception = new Exception(throwable);
    when(intentReplyRepository.findByIntent(anyString()))
          .thenThrow(new RuntimeException("some_exception", exception.getCause()));
    Assertions.assertThrows(IntentSaveException.class, () -> {
          chatReplyService.saveIntent(intentToSave);
    });
  }

  @Test
  public void testSaveIntentSuccess() {
    when(intentReplyRepository.findByIntent(any(String.class)))
        .thenReturn(intentReply);

    when(intentReplyRepository.save(intentReply))
        .thenReturn(intentReply);
    IntentReply actualReply = chatReplyService.saveIntent(intentToSave);
    Assertions.assertEquals(intentReply.getConfidenceThreshold(), actualReply.getConfidenceThreshold());
  }

  @Test
  public void testSaveIntentInvalidInput(){
    when(intentReplyRepository.findByIntent(anyString()))
        .thenReturn(null);
    Assertions.assertThrows(InvalidInputException.class, () -> {
      chatReplyService.saveIntent(intentToSave);
    });
  }

  @Test
  public void testSaveIntentInvalidInputThreshold(){
    intentToSave.setConfidenceThreshold("101.0003");
    when(intentReplyRepository.findByIntent(anyString()))
        .thenReturn(intentReply);
    Assertions.assertThrows(InvalidInputException.class, () -> {
      chatReplyService.saveIntent(intentToSave);
    });
  }


}
