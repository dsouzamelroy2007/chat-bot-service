package com.ultimate.cb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ultimate.cb.constants.ChatConstants;
import com.ultimate.cb.exception.IntentFetchException;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.util.ChatDataUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class RestService {

    @Autowired
    RestTemplate restTemplate;

    @Value("${ultimateAI.api.botIdentifier}")
    private String botIdentifier;

    @Value("${ultimateAI.api.authorizationKey}")
    private String authorizationKey;

    public <T> T get(String url, Class<T> returnType) {
        try {
            ResponseEntity<T> responseEntity = restTemplate.getForEntity(url, returnType);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return responseEntity.getBody();
            } else {
                throw new IntentFetchException(HttpStatus.INTERNAL_SERVER_ERROR,"Exception in get request error code: " + responseEntity.getStatusCodeValue());
            }
        } catch (Exception e) {
            log.error("Exception while getForEntity using {} ", url, e);
            throw new IntentFetchException(HttpStatus.INTERNAL_SERVER_ERROR, e.getCause());
        }
    }

    public <T> T post(String url, ChatMessage message,  Class<T> returnType) {
        final Instant startTime = Instant.now();
        try {
            final HashMap<String, Object> map = new HashMap<>();
            {
                map.put(ChatConstants.BOT_ID,   message.getBotId());
                map.put(ChatConstants.MESSAGE, message.getMessage());
            }
            final  HttpHeaders header = new HttpHeaders();
            {
                header.add(ChatConstants.CONTENT_TYPE, "application/json");
                header.add(ChatConstants.AUTHORIZATION, authorizationKey);
            }
            String jsonString = ChatDataUtil.getObjectAsString(message);
            HttpEntity<String> entity = new HttpEntity<>(jsonString, header);
            ResponseEntity<T> re1 = restTemplate.postForEntity(url, entity, returnType);
            return re1.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Exception while postForEntity using {} ", url, e);
            throw new IntentFetchException(e.getStatusCode(), e.getMessage());
        } catch (JsonProcessingException e){
            log.error("Exception while postForEntity using {} ", url, e);
            throw new IntentFetchException(e.getMessage());
        }finally {
            log.info("RequestType: {}, Response_Code: {}, Timestamp: {} ms", "fetch_intents_from_AI", HttpStatus.OK,
                Duration
                    .between(startTime, Instant.now())
                    .toMillis());
        }
    }

}
