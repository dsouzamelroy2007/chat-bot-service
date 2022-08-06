package com.ultimate.cb.service;

import com.ultimate.cb.constants.ChatConstants;
import com.ultimate.cb.constants.ExceptionConstants;
import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.exception.IntentSaveException;
import com.ultimate.cb.exception.InvalidInputException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.model.PredictedIntent;
import com.ultimate.cb.model.PredictedIntentCollection;
import com.ultimate.cb.repository.IntentReplyRepository;
import com.ultimate.cb.util.ChatDataUtil;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ChatReplyService {

  @Autowired
  private IntentReplyRepository intentReplyRepository;

  @Autowired
  private RestService restService;

  @Value("${ultimateAI.api.baseURL}")
  private String aIAPIBaseUrl;

  public ChatReply getReplyForUserMessage(ChatMessage chatMessage){
    PredictedIntentCollection predictedIntentCollection =  restService.post(aIAPIBaseUrl + File.separator + ChatConstants.INTENTS, chatMessage, PredictedIntentCollection.class);
    PredictedIntent predictedIntent = ChatDataUtil.getPredictedIntentFromAI(predictedIntentCollection.getIntents());
    IntentReply intentReply = intentReplyRepository.findByIntentAndConfidenceThresholdLessThanEqual(predictedIntent.getIntent(), predictedIntent.getConfidence());
    return ChatDataUtil.getChatReplyFromIntent(intentReply);
  }

  @Transactional
  public IntentReply saveIntent(IntentModel newIntent){
    try {
      IntentReply intentFromDB = intentReplyRepository.findByIntent(newIntent.getIntent());
      intentFromDB = ChatDataUtil.copyMissingIntentData(intentFromDB, newIntent);
      return intentReplyRepository.save(intentFromDB);
    }catch (InvalidInputException | NumberFormatException e){
      log.error(ExceptionConstants.INTENT_SAVE_EXCEPTION+" :: {} :: " , newIntent, e);
      throw new InvalidInputException(e.getMessage());
    }catch (Exception e){
      log.error(ExceptionConstants.INTENT_SAVE_EXCEPTION+" :: {} :: " , newIntent, e);
      throw new IntentSaveException(HttpStatus.INTERNAL_SERVER_ERROR, e.getCause());
    }
  }

}
