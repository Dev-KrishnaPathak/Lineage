package com.lineage.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import com.falkordb.GraphContextGenerator;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component
public class GraphLoader {

    private static final Logger logger = LoggerFactory.getLogger(GraphLoader.class);

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
        int changedEdges = 0;
        for (JsonNode file : snapshot.files()) {
            int number = file.path("number").asInt();
            String path = file.path("filename").asText();
            if (number < 1 || path.isBlank()) {
                continue;
            }
            Map<String, Object> params = Map.of(
                    "number", number,
                    "path", path);
            run(graph, Queries.MERGE_FILE, Map.of("path", path));
            run(graph, Queries.LINK_CHANGED, params);
            changedEdges++;
        }
        int reviewedEdges = 0;
        for (JsonNode review : snapshot.reviews()) {
            if (isBot(review.path("user"))) {
                continue;
            }
            if (linkPerson(graph, review.path("user"), review.path("number").asInt(),
                    Queries.LINK_REVIEWED)) {
                reviewedEdges++;
            }
        }
        int commentedEdges = 0;
        for (JsonNode comment : snapshot.comments()) {
            if (linkPerson(graph, comment.path("user"), comment.path("number").asInt(),
                    Queries.LINK_COMMENTED)) {
                commentedEdges++;
            }
        }
        logger.info("Created {} REVIEWED, {} COMMENTED_ON and {} CHANGED edges",
                reviewedEdges, commentedEdges, changedEdges);
    }

    public void loadDemo() {
        GraphContextGenerator graph = graphClient.projectGraph();
        run(graph, Queries.MERGE_PERSON, Map.of("login", "demo-user", "name", "Demo User"));
        run(graph, Queries.MERGE_PERSON, Map.of("login", "cache-maintainer", "name", "Cache Maintainer"));
        run(graph, Queries.MERGE_ISSUE, Map.of(
                "number", 1, "title", "Improve caching", "body", "Cache API responses",
                "state", "open", "createdAt", "2026-01-01T00:00:00Z", "url", "demo://issues/1"));
        run(graph, Queries.MERGE_PR, Map.of(
                "number", 2, "title", "Add cache layer", "body", "Adds a cache layer",
                "state", "open", "createdAt", "2026-01-02T00:00:00Z",
                "mergedAt", "", "url", "demo://pulls/2"));
        run(graph, Queries.MERGE_PR, Map.of(
                "number", 3, "title", "Tune cache invalidation", "body", "Improves cache invalidation",
                "state", "closed", "createdAt", "2025-12-15T00:00:00Z",
                "mergedAt", "2025-12-20T00:00:00Z", "url", "demo://pulls/3"));
        run(graph, Queries.MERGE_FILE, Map.of("path", "src/cache/CacheService.java"));
        run(graph, Queries.MERGE_TOPIC, Map.of("name", "caching"));
        run(graph, Queries.LINK_AUTHORED, Map.of("login", "demo-user", "number", 2));
        run(graph, Queries.LINK_REVIEWED, Map.of("login", "cache-maintainer", "number", 3));
        run(graph, Queries.LINK_CHANGED, Map.of("number", 2, "path", "src/cache/CacheService.java"));
        run(graph, Queries.LINK_CHANGED, Map.of("number", 3, "path", "src/cache/CacheService.java"));
        run(graph, Queries.LINK_ABOUT, Map.of("number", 1, "name", "caching"));
        run(graph, Queries.LINK_ABOUT, Map.of("number", 2, "name", "caching"));
        run(graph, Queries.LINK_ABOUT, Map.of("number", 3, "name", "caching"));
        run(graph, Queries.LINK_COMMENTED, Map.of("login", "cache-maintainer", "number", 2));
        run(graph, Queries.MERGE_DECISION, Map.of(
                "id", "demo-cache-decision", "summary", "Use a bounded cache with explicit invalidation for API responses.",
                "date", "2026-01-03T00:00:00Z", "sourceUrl", "demo://pulls/2"));
        run(graph, Queries.LINK_DECISION_MADE_IN, Map.of("id", "demo-cache-decision", "number", 2));
        run(graph, Queries.LINK_DECISION_ABOUT, Map.of("id", "demo-cache-decision", "name", "caching"));
        run(graph, Queries.LINK_DECISION_AFFECTS, Map.of(
                "id", "demo-cache-decision", "path", "src/cache/CacheService.java"));
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

    private boolean linkPerson(GraphContextGenerator graph, JsonNode user, int number, String query) {
        if (!user.isObject() || number < 1) {
            return false;
        }
        String login = user.path("login").asText();
        if (login.isBlank()) {
            return false;
        }
        run(graph, Queries.MERGE_PERSON, Map.of("login", login, "name", login));
        run(graph, query, Map.of("login", login, "number", number));
        return true;
    }

    private boolean isBot(JsonNode user) {
        if (!user.isObject()) {
            return false;
        }
        String type = user.path("type").asText();
        String login = user.path("login").asText();
        return "Bot".equalsIgnoreCase(type) || login.toLowerCase().endsWith("[bot]");
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

}
