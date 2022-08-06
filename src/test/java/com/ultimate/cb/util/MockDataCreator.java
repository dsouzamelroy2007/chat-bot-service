package com.ultimate.cb.util;

import com.ultimate.cb.domain.IntentReply;
import com.ultimate.cb.model.ChatMessage;
import com.ultimate.cb.model.ChatReply;
import com.ultimate.cb.model.IntentModel;
import com.ultimate.cb.model.PredictedIntent;
import java.math.BigDecimal;
import java.time.Instant;

public class MockDataCreator {

    private static Instant timeStamp = Instant.now();
    public static IntentReply getIntentReplyForTest(){
        BigDecimal confidenceThreshold = new BigDecimal(0.9984545);
        return new IntentReply(null, "Thank you", "The user is thanking the bot", "0.9984545", "See you soon!!" );
    }

    public static ChatMessage getChatMessageForTest(){
        return new ChatMessage("23432432243234", "Thank you");
    }

    public static ChatReply getChatReplyForTest(){
        return new ChatReply("See you soon!!", timeStamp);
    }

    public static IntentModel getIntentToSaveForTest(){
        return new IntentModel("Thank you", "The user is thanking the bot", "0.9984545", null);
    }

    public static PredictedIntent getPredictedIntentForTest(){
        BigDecimal confidenceThreshold = new BigDecimal(0.997923374176025390625);
        return new PredictedIntent("Goodbye", confidenceThreshold);
    }

}


