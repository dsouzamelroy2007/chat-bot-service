package com.mel.cb.util;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.mel.cb.constants.ChatConstants;
import com.mel.cb.constants.ExceptionConstants;
import com.mel.cb.exception.IntentParseException;
import com.mel.cb.model.ChatReply;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;


@Slf4j
public class ChatDataUtil {

  private static final JsonMapper objectMapper;

  static{
    objectMapper = JsonMapper.builder()
        .changeDefaultVisibility(checker -> checker.withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
        .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .build();
  }

  public static ChatReply getChatReplyFromText(String replyText) {
    if(replyText == null || replyText.isBlank()){
      return new ChatReply(ChatConstants.NO_REPLY_AVAILABLE, Instant.now(), null);
    }
    return new ChatReply(replyText, Instant.now(), null);
  }

  public static String getObjectAsString(Object obj) {
    return objectMapper.writeValueAsString(obj);
  }

  public static <T> T fromJson(String json, Class<T> valueType){
    try {
      return objectMapper.readValue(json, valueType);
    }catch(Exception e){
      throw new IntentParseException(ExceptionConstants.FAILED_TO_CONVERT_JSON + json);
    }
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
