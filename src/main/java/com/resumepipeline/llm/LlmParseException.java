package com.resumepipeline.llm;

/** The model answered, but not with JSON of the requested shape. */
public class LlmParseException extends RuntimeException {
    public LlmParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
