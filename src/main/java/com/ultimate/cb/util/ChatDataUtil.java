package com.ultimate.cb.util;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ultimate.cb.constants.ChatConstants;
import com.ultimate.cb.constants.ExceptionConstants;
import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.exception.IntentParseException;
import com.ultimate.cb.exception.InvalidInputException;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.model.PredictedIntent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


@Slf4j
public class ChatDataUtil {

  private static final ObjectMapper objectMapper;

  static{
    objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  }
  public static PredictedIntent getPredictedIntentFromAI(List<PredictedIntent> possibleAIIntents){

    PredictedIntent predictedIntent = new PredictedIntent("",new BigDecimal(0.0));
    possibleAIIntents.stream().forEach( intent -> {
         int comparisonResult = intent.getConfidence().compareTo(predictedIntent.getConfidence());
         if( comparisonResult >= 0 ){
           predictedIntent.setConfidence(intent.getConfidence());
           predictedIntent.setIntent(intent.getIntent());
         }
     });

     return predictedIntent;
  }

  public static ChatReply getChatReplyFromIntent(IntentReply intentReply) {
    if(intentReply == null || intentReply.getReply() == null || intentReply.getReply().equals("")){
      return new ChatReply(ChatConstants.NO_REPLY_AVAILABLE, Instant.now());
    }
    return new ChatReply(intentReply.getReply(), Instant.now());
  }

  public static IntentReply copyMissingIntentData(IntentReply intent, IntentModel newIntent) {
    if(intent == null){
      intent = new IntentReply();
    }
    if(StringUtils.isNotBlank(newIntent.getReply())){
      intent.setReply(newIntent.getReply());
    }
    intent.setConfidenceThreshold(getConfidenceThreshold(newIntent.getConfidenceThreshold()));
    intent.setIntent(newIntent.getIntent());
    if(StringUtils.isNotBlank(newIntent.getDescription())){
      intent.setDescription(newIntent.getDescription());
    }
    return validateIntentBeforeSaving(intent);
  }

  private static String getConfidenceThreshold(String confidenceThresholdStr){
    BigDecimal confidenceThreshold = new BigDecimal(confidenceThresholdStr).setScale(ChatConstants.CONFIDENCE_THRESHOLD_SCALE, RoundingMode.HALF_UP);
    BigDecimal lower = new BigDecimal(0);
    BigDecimal upper = new BigDecimal(100);
    if(confidenceThreshold.compareTo(lower) >= 0 && confidenceThreshold.compareTo(upper) <= 0){
      return confidenceThresholdStr;
    }
    throw new InvalidInputException(ExceptionConstants.INCORRECT_RANGE_CONFIDENCE_THRESHOLD);
  }

  private static IntentReply validateIntentBeforeSaving(IntentReply intentReply) throws InvalidInputException{
    if(StringUtils.isBlank(intentReply.getIntent()) || StringUtils.isBlank(intentReply.getReply()) || intentReply.getConfidenceThreshold() == null){
      throw new InvalidInputException(ExceptionConstants.INTENT_INPUT_INVALID);
    }
    return intentReply;
  }

  public static String getObjectAsString(Object obj) throws JsonProcessingException {
    return objectMapper.writeValueAsString(obj);
  }

  public static <T> T fromJson(String json, Class<T> valueType){
    try {
      return objectMapper.readValue(json, valueType);
    }catch(Exception e){
      throw getRuntimeException(e, ExceptionConstants.FAILED_TO_CONVERT_JSON + json );
    }
  }

  public static RuntimeException getRuntimeException(Throwable t, String msg) {
    if (t != null && RuntimeException.class.isInstance(t)) {
      return RuntimeException.class.cast(t);
    }
    return new IntentParseException(msg);
  }

  public static String readFileData(String filename) {
    String data = "";
    try {
      Resource resource = new ClassPathResource("/" + filename);
      Path p = Paths.get(((ClassPathResource) resource).getURI());
      data = new String(Files.readAllBytes(p));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return data;
  }

}
