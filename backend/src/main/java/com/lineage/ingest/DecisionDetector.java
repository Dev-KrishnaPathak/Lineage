package com.lineage.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.falkordb.GraphContextGenerator;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class DecisionDetector {

    private final GraphClient graphClient;
    private final String apiKey;

    public DecisionDetector(
            GraphClient graphClient,
            @Value("${lineage.llm.api-key:}") String apiKey) {
        this.graphClient = graphClient;
        this.apiKey = apiKey;
    }

    public void detect(GithubFetcher.RepositorySnapshot snapshot) {
        if (apiKey.isBlank()) {
            return;
        }
        ChatModel model = OpenAiChatModel.builder().apiKey(apiKey).build();
        GraphContextGenerator graph = graphClient.projectGraph();
        for (JsonNode item : snapshot.pullRequests()) {
            String body = item.path("body").asText("");
            if (body.length() < 500) {
                continue;
            }
            String summary = model.chat("""
                    Extract one concise engineering decision from this GitHub thread.
                    Return only the decision summary, or NONE if there is no decision.
                    Thread:
                    """ + body);
            if (summary == null || summary.isBlank() || summary.equalsIgnoreCase("NONE")) {
                continue;
            }
            String id = "pr-" + item.path("number").asInt() + "-decision";
            graph.query(Queries.MERGE_DECISION, Map.of(
                    "id", id, "summary", summary, "date", Instant.now().toString(),
                    "sourceUrl", item.path("html_url").asText()));
            graph.query(Queries.LINK_DECISION_MADE_IN, Map.of(
                    "id", id, "number", item.path("number").asInt()));
            int pullRequestNumber = item.path("number").asInt();
            for (JsonNode file : snapshot.files()) {
                if (file.path("number").asInt() == pullRequestNumber
                        && !file.path("filename").asText().isBlank()) {
                    graph.query(Queries.LINK_DECISION_AFFECTS, Map.of(
                            "id", id, "path", file.path("filename").asText()));
                }
            }
        }
    }
}
