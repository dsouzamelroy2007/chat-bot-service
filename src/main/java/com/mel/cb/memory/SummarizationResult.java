package com.mel.cb.memory;

import java.util.List;

/** Output of {@link ConversationSummarizer#summarize}: the merged rolling summary plus any newly-spotted durable facts about the user. */
public record SummarizationResult(String summary, List<String> facts) {
}
