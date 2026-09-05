package com.mel.cb.util;

import com.mel.cb.model.ChatMessage;
import com.mel.cb.model.ChatReply;
import java.time.Instant;

public class MockDataCreator {

    private static Instant timeStamp = Instant.now();

    public static ChatMessage getChatMessageForTest(){
        return new ChatMessage("23432432243234", "user-123", "Thank you", null);
    }

    public static ChatReply getChatReplyForTest(){
        return new ChatReply("See you soon!!", timeStamp, null, null, null);
    }

}
