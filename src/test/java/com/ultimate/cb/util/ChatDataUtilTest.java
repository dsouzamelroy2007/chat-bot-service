package com.ultimate.cb.util;

import static com.ultimate.cb.util.ChatDataUtil.copyMissingIntentData;
import static com.ultimate.cb.util.ChatDataUtil.fromJson;
import static com.ultimate.cb.util.ChatDataUtil.getChatReplyFromIntent;
import static com.ultimate.cb.util.ChatDataUtil.getPredictedIntentFromAI;
import static com.ultimate.cb.util.ChatDataUtil.readFileData;
import static com.ultimate.cb.util.MockDataCreator.getChatMessageForTest;
import static com.ultimate.cb.util.MockDataCreator.getChatReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentReplyForTest;
import static com.ultimate.cb.util.MockDataCreator.getIntentToSaveForTest;
import static com.ultimate.cb.util.MockDataCreator.getPredictedIntentForTest;

import com.ultimate.cb.constants.ChatConstants;
import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.exception.IntentParseException;
import com.ultimate.cb.exception.InvalidInputException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.model.PredictedIntent;
import com.ultimate.cb.model.PredictedIntentCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChatDataUtilTest {
  private ChatReply chatReply;

  private ChatMessage chatMessage;

  private IntentReply intentReply;

  private IntentModel intentToSave;

  private PredictedIntent predictedIntent;

  @BeforeEach
  public void setUp(){
    chatReply = getChatReplyForTest();
    chatMessage = getChatMessageForTest();
    intentReply = getIntentReplyForTest();
    intentToSave = getIntentToSaveForTest();
    predictedIntent = getPredictedIntentForTest();
  }

  @Test
  public void testGetPredictedIntentFromAIFailure(){
    String intentsJsonStr = readFileData("test_Intents_bad_data.json");
    Assertions.assertThrows(IntentParseException.class, () -> {
      fromJson(intentsJsonStr, PredictedIntentCollection.class);
    });
  }

  @Test
  public void testGetPredictedIntentFromAISuccess(){
      String intentsJsonStr = readFileData("test_Intents.json");
      PredictedIntentCollection predictedIntentCollection = fromJson(intentsJsonStr, PredictedIntentCollection.class);
      PredictedIntent actualIntent =  getPredictedIntentFromAI(predictedIntentCollection.getIntents());
      Assertions.assertEquals(predictedIntent, actualIntent);
  }

  @Test
  public void testGetDefaultChatReplyFromIntent(){
    intentReply.setReply(null);
    ChatReply actualReply = getChatReplyFromIntent(intentReply);
    Assertions.assertEquals(ChatConstants.NO_REPLY_AVAILABLE, actualReply.getReply());
  }

  @Test
  public void testGetChatReplyFromIntent(){
    ChatReply actualReply = getChatReplyFromIntent(intentReply);
    Assertions.assertEquals(chatReply.getReply(), actualReply.getReply());
  }

  @Test
  public void testCopyMissingIntentDataSuccess(){
    IntentReply actualIntentToBeSaved = copyMissingIntentData(intentReply,intentToSave);
    Assertions.assertEquals(intentToSave.getIntent(), actualIntentToBeSaved.getIntent());

  }

  @Test
  public void testCopyMissingIntentDataFailure(){
    intentToSave.setConfidenceThreshold("342.43243");
    Assertions.assertThrows(InvalidInputException.class, () -> {
      copyMissingIntentData(intentReply,intentToSave);
    });
  }
}
