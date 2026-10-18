package com.lineage.agents;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AgentModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentModel.class);
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(8);

    private final ChatModel model;

    public AgentModel(@Value("${lineage.llm.api-key:}") String apiKey) {
        this.model = apiKey.isBlank()
                ? null
                : OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .timeout(LLM_TIMEOUT)
                        .maxRetries(0)
                        .build();
    }

    public String respond(String prompt, String fallback) {
        if (model == null) {
            return fallback;
        }
        try {
            return model.chat(prompt);
        } catch (RuntimeException exception) {
            LOGGER.warn("LLM response unavailable; returning graph-based answer");
            return fallback;
        }
    }
}
