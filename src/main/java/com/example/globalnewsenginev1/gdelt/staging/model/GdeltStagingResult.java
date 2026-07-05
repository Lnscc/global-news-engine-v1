package com.example.globalnewsenginev1.gdelt.staging.model;

public record GdeltStagingResult(long eventsStaged, long mentionsStaged, long gkgStaged, long errors) {

    public long totalStaged() {
        return eventsStaged + mentionsStaged + gkgStaged;
    }

    public GdeltStagingResult plus(GdeltStagingResult other) {
        return new GdeltStagingResult(
                eventsStaged + other.eventsStaged(),
                mentionsStaged + other.mentionsStaged(),
                gkgStaged + other.gkgStaged(),
                errors + other.errors());
    }
}
