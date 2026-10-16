package com.lineage.memory;

import com.falkordb.GraphContextGenerator;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ContributorMemory {

    private final GraphClient graphClient;

    public ContributorMemory(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public void recordWorkedOn(String login, String filePath) {
        GraphContextGenerator graph = contributorGraph(login);
        graph.query(Queries.MERGE_CONTRIBUTOR, Map.of("login", login));
        graph.query(Queries.MERGE_CONTRIBUTOR_FILE, Map.of("path", filePath));
        graph.query(Queries.LINK_WORKED_ON, Map.of(
                "login", login, "path", filePath, "lastAt", Instant.now().toString()));
    }

    public void recordKnowledge(String login, String topic, double score) {
        GraphContextGenerator graph = contributorGraph(login);
        graph.query(Queries.MERGE_CONTRIBUTOR, Map.of("login", login));
        graph.query(Queries.MERGE_CONTRIBUTOR_TOPIC, Map.of("name", topic));
        graph.query(Queries.LINK_KNOWS, Map.of("login", login, "name", topic, "score", score));
    }

    public void recordAttempt(String login, int issueNumber, String outcome) {
        GraphContextGenerator graph = contributorGraph(login);
        graph.query(Queries.MERGE_CONTRIBUTOR, Map.of("login", login));
        graph.query(Queries.MERGE_CONTRIBUTOR_ISSUE, Map.of("number", issueNumber));
        graph.query(Queries.LINK_ATTEMPTED, Map.of(
                "login", login, "number", issueNumber, "outcome", outcome));
    }

    private GraphContextGenerator contributorGraph(String login) {
        if (login == null || !login.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid contributor login");
        }
        return graphClient.graph("contrib_" + login);
    }
}
