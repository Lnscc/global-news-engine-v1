package com.example.globalnewsenginev1.gdelt.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GdeltMentionRecord(
        long globalEventId,
        LocalDateTime eventTimeDate,
        LocalDateTime mentionTimeDate,
        int mentionType,
        String mentionSourceName,
        String mentionIdentifier,
        Integer sentenceId,
        Integer actor1CharOffset,
        Integer actor2CharOffset,
        Integer actionCharOffset,
        boolean inRawText,
        Integer confidence,
        Integer mentionDocLen,
        BigDecimal mentionDocTone,
        String mentionDocTranslationInfo,
        String extras
) {
}
