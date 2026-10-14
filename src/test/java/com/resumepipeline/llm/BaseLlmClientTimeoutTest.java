package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class BaseLlmClientTimeoutTest {

    @Test
    void findsTimeoutDeepInTheCauseChain() {
        // Shape thrown by OpenAiCompatibleLlmClient: RuntimeException -> RestClientException -> SocketTimeoutException
        Throwable e = new RuntimeException("LLM call failed",
                new RuntimeException("I/O error", new SocketTimeoutException("Read timed out")));
        assertTrue(BaseLlmClient.isTimeout(e));
        assertTrue(BaseLlmClient.isTimeout(new RuntimeException(new TimeoutException())));
    }

    @Test
    void otherFailuresAreNotTimeouts() {
        assertFalse(BaseLlmClient.isTimeout(new RuntimeException("429", new IllegalStateException())));
    }
}
