package com.lineage.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import com.falkordb.GraphContextGenerator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GraphLoader {

    private final GraphClient graphClient;

    public GraphLoader(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public void load(GithubFetcher.RepositorySnapshot snapshot) {
        GraphContextGenerator graph = graphClient.projectGraph();
        for (JsonNode issue : snapshot.issues()) {
            if (issue.has("pull_request")) {
                continue;
            }
            run(graph, Queries.MERGE_ISSUE, issueParams(issue));
            linkAuthor(graph, issue, Queries.LINK_AUTHORED);
            linkTopics(graph, issue);
        }
        for (JsonNode pullRequest : snapshot.pullRequests()) {
            run(graph, Queries.MERGE_PR, prParams(pullRequest));
            linkAuthor(graph, pullRequest, Queries.LINK_AUTHORED);
            linkTopics(graph, pullRequest);
        }
        for (JsonNode file : snapshot.files()) {
            Map<String, Object> params = Map.of(
                    "number", file.path("number").asInt(),
                    "path", file.path("filename").asText());
            run(graph, Queries.MERGE_FILE, Map.of("path", params.get("path")));
            run(graph, Queries.LINK_CHANGED, params);
        }
        for (JsonNode review : snapshot.reviews()) {
            linkPerson(graph, review.path("user"), review.path("pull_request_url").asText(),
                    Queries.LINK_REVIEWED);
        }
        for (JsonNode comment : snapshot.comments()) {
            int number = extractNumber(comment.path("issue_url").asText());
            if (number > 0) {
                linkPerson(graph, comment.path("user"), number, Queries.LINK_COMMENTED);
            }
        }
    }

    public void loadDemo() {
        GraphContextGenerator graph = graphClient.projectGraph();
        run(graph, Queries.MERGE_PERSON, Map.of("login", "demo-user", "name", "Demo User"));
        run(graph, Queries.MERGE_ISSUE, Map.of(
                "number", 1, "title", "Improve caching", "body", "Cache API responses",
                "state", "open", "createdAt", "2026-01-01T00:00:00Z", "url", "demo://issues/1"));
        run(graph, Queries.MERGE_PR, Map.of(
                "number", 2, "title", "Add cache layer", "body", "Adds a cache layer",
                "state", "open", "createdAt", "2026-01-02T00:00:00Z",
                "mergedAt", "", "url", "demo://pulls/2"));
        run(graph, Queries.MERGE_FILE, Map.of("path", "src/cache/CacheService.java"));
        run(graph, Queries.MERGE_TOPIC, Map.of("name", "caching"));
        run(graph, Queries.LINK_AUTHORED, Map.of("login", "demo-user", "number", 2));
        run(graph, Queries.LINK_CHANGED, Map.of("number", 2, "path", "src/cache/CacheService.java"));
        run(graph, Queries.LINK_ABOUT, Map.of("number", 1, "name", "caching"));
        run(graph, Queries.LINK_ABOUT, Map.of("number", 2, "name", "caching"));
    }

    private void linkAuthor(GraphContextGenerator graph, JsonNode item, String query) {
        JsonNode user = item.path("user");
        if (!user.path("login").isMissingNode()) {
            run(graph, Queries.MERGE_PERSON, Map.of(
                    "login", user.path("login").asText(),
                    "name", user.path("name").asText(user.path("login").asText())));
            run(graph, query, Map.of("login", user.path("login").asText(),
                    "number", item.path("number").asInt()));
        }
    }

    private void linkPerson(GraphContextGenerator graph, JsonNode user, String url, String query) {
        int number = extractNumber(url);
        if (number > 0) {
            linkPerson(graph, user, number, query);
        }
    }

    private void linkPerson(GraphContextGenerator graph, JsonNode user, int number, String query) {
        String login = user.path("login").asText();
        if (!login.isBlank()) {
            run(graph, Queries.MERGE_PERSON, Map.of("login", login, "name", login));
            run(graph, query, Map.of("login", login, "number", number));
        }
    }

    private void linkTopics(GraphContextGenerator graph, JsonNode item) {
        int number = item.path("number").asInt();
        for (JsonNode label : item.path("labels")) {
            String topic = label.path("name").asText();
            if (!topic.isBlank()) {
                run(graph, Queries.MERGE_TOPIC, Map.of("name", topic));
                run(graph, Queries.LINK_ABOUT, Map.of("number", number, "name", topic));
            }
        }
    }

    private Map<String, Object> issueParams(JsonNode issue) {
        Map<String, Object> params = new HashMap<>();
        params.put("number", issue.path("number").asInt());
        params.put("title", issue.path("title").asText());
        params.put("body", issue.path("body").asText(""));
        params.put("state", issue.path("state").asText());
        params.put("createdAt", issue.path("created_at").asText());
        params.put("url", issue.path("html_url").asText());
        return params;
    }

    private Map<String, Object> prParams(JsonNode pr) {
        Map<String, Object> params = issueParams(pr);
        params.put("mergedAt", pr.path("merged_at").asText(""));
        return params;
    }

    private void run(GraphContextGenerator graph, String query, Map<String, Object> params) {
        graph.query(query, params);
    }

    private int extractNumber(String url) {
        if (url == null || url.isBlank()) {
            return 0;
        }
        String[] parts = url.split("/");
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
