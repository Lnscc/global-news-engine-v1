package com.example.globalnewsenginev1.gdelt.staging.parser;

public class GdeltParseException extends RuntimeException {

    private final String code;

    GdeltParseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
