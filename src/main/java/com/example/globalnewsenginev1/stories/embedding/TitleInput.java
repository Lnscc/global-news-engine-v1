package com.example.globalnewsenginev1.stories.embedding;

public record TitleInput(String normalizedTitle, String titleInputHash, TitleUsability usability) {

    public enum TitleUsability {
        USABLE,
        TITLE_MISSING,
        TITLE_GENERIC
    }
}
