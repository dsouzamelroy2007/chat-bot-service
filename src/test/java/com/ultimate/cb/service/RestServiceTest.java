package com.ultimate.cb.service;

import static com.ultimate.cb.util.ChatDataUtil.fromJson;
import static com.ultimate.cb.util.ChatDataUtil.readFileData;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ultimate.cb.exception.IntentFetchException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.PredictedIntentCollection;
import com.ultimate.cb.util.ChatDataUtil;
import com.ultimate.cb.util.MockDataCreator;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
public class RestServiceTest {

  @InjectMocks
  private RestService restService;

  @Mock
  private RestTemplate restTemplate;

  private ChatMessage chatMessage;

  @BeforeEach
  public void setUp() throws JsonProcessingException {
    chatMessage = MockDataCreator.getChatMessageForTest();
    String jsonString = ChatDataUtil.getObjectAsString(chatMessage);
  }

  @Test
  public void testGetResourceFailure(){
    Throwable throwable = new Throwable("GatewayTime Error");
    Exception exception = new Exception("Rest call exception", throwable);
    when(restTemplate.getForEntity("getURL", Map.class))
        .thenThrow(new RuntimeException("Error while", exception.getCause()));

    Assertions.assertThrows(IntentFetchException.class, () -> {
      restService.get("getURL", Map.class);
    });
  }

  @Test
  public void testGetResourceSuccess(){
    String intentsJsonStr = readFileData("test_Intents.json");
    PredictedIntentCollection predictedIntentCollection = fromJson(intentsJsonStr, PredictedIntentCollection.class);
    ResponseEntity<PredictedIntentCollection> responseEntity = new ResponseEntity<>( predictedIntentCollection, HttpStatus.OK);

    when(restTemplate.getForEntity("getURL", PredictedIntentCollection.class))
        .thenReturn(responseEntity);

    PredictedIntentCollection actualResponseEntity = restService.get("getURL", PredictedIntentCollection.class);
    Assertions.assertEquals(responseEntity.getBody(), actualResponseEntity);

  }

  @Test
  public void testPostResourceFailure(){
    ChatMessage chatMessage = MockDataCreator.getChatMessageForTest();
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request, missing paramters"));
    Assertions.assertThrows(IntentFetchException.class, () -> {
      restService.post("postUrl", chatMessage, PredictedIntentCollection.class);
    });
  }

  @Test
  public void testPostResourceSuccess() throws JsonProcessingException {
    String intentsJsonStr = readFileData("test_Intents.json");
    PredictedIntentCollection predictedIntentCollection = fromJson(intentsJsonStr, PredictedIntentCollection.class);
    ResponseEntity<PredictedIntentCollection> responseEntity = new ResponseEntity<>( predictedIntentCollection, HttpStatus.OK);

    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(responseEntity);

    PredictedIntentCollection actualResponse = restService.post("postUrl", chatMessage, PredictedIntentCollection.class);
    Assertions.assertEquals(responseEntity.getBody(), actualResponse);

  }
}
