package com.lineage.agents;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentModel {

    private final ChatModel model;

    public AgentModel(@Value("${lineage.llm.api-key:}") String apiKey) {
        this.model = apiKey.isBlank() ? null : OpenAiChatModel.builder().apiKey(apiKey).build();
    }

    public String respond(String prompt, String fallback) {
        return model == null ? fallback : model.chat(prompt);
    }
}
