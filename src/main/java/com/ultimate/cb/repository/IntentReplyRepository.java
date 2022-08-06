package com.ultimate.cb.repository;

import com.ultimate.cb.domain.IntentReply;
import java.math.BigDecimal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IntentReplyRepository extends MongoRepository<IntentReply, String> {

  IntentReply findByIntentAndConfidenceThresholdLessThanEqual(String intent, BigDecimal confidenceThreshold);

  IntentReply findByIntent(String intent);

}