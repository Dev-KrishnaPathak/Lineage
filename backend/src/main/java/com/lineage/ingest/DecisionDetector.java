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

    private static final int MINIMUM_THREAD_LENGTH = 500;
    private static final String NO_DECISION = "NONE";
    private static final String DECISION_ID_FORMAT = "pr-%d-decision";

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
            if (item.path("body").asText("").length() < MINIMUM_THREAD_LENGTH) {
                continue;
            }
            String summary = extractDecision(model, item.path("body").asText(""));
            if (summary == null) {
                continue;
            }
            persistDecision(graph, item, summary, snapshot.files());
        }
    }

    private String extractDecision(ChatModel model, String body) {
        String summary = model.chat("""
                Extract one concise engineering decision from this GitHub thread.
                Return only the decision summary, or NONE if there is no decision.
                Thread:
                """ + body);
        return summary == null || summary.isBlank() || summary.equalsIgnoreCase(NO_DECISION)
                ? null
                : summary;
    }

    private void persistDecision(
            GraphContextGenerator graph,
            JsonNode pullRequest,
            String summary,
            Iterable<JsonNode> files) {
        int pullRequestNumber = pullRequest.path("number").asInt();
        String decisionId = DECISION_ID_FORMAT.formatted(pullRequestNumber);
        graph.query(Queries.MERGE_DECISION, Map.of(
                "id", decisionId, "summary", summary, "date", Instant.now().toString(),
                "sourceUrl", pullRequest.path("html_url").asText()));
        graph.query(Queries.LINK_DECISION_MADE_IN, Map.of(
                "id", decisionId, "number", pullRequestNumber));
        linkAffectedFiles(graph, decisionId, pullRequestNumber, files);
    }

    private void linkAffectedFiles(
            GraphContextGenerator graph,
            String decisionId,
            int pullRequestNumber,
            Iterable<JsonNode> files) {
        for (JsonNode file : files) {
            String path = file.path("filename").asText();
            if (file.path("number").asInt() == pullRequestNumber && !path.isBlank()) {
                graph.query(Queries.LINK_DECISION_AFFECTS, Map.of(
                        "id", decisionId, "path", path));
            }
        }
    }
}
