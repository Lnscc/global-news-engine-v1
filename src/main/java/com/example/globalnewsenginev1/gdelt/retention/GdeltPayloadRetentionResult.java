package com.example.globalnewsenginev1.gdelt.retention;

public record GdeltPayloadRetentionResult(long eventsDeleted, long mentionsDeleted, long gkgDeleted) {

    public long totalDeleted() {
        return eventsDeleted + mentionsDeleted + gkgDeleted;
    }

    public GdeltPayloadRetentionResult plus(GdeltPayloadRetentionResult other) {
        return new GdeltPayloadRetentionResult(
                eventsDeleted + other.eventsDeleted(),
                mentionsDeleted + other.mentionsDeleted(),
                gkgDeleted + other.gkgDeleted());
    }
}
