package com.lineage.tools;

import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GraphTools {

    private final GraphClient graphClient;

    public GraphTools(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public ToolResult suggestReviewers(int prNumber) {
        return run(Queries.SUGGEST_REVIEWERS, Map.of("number", prNumber), "login", "score");
    }

    public ToolResult findReviewers(int prNumber) {
        return run(Queries.FIND_REVIEWERS, Map.of("number", prNumber), "login");
    }

    public ToolResult findPullRequest(int prNumber) {
        return run(Queries.FIND_PR, Map.of("number", prNumber), "number", "title");
    }

    public ToolResult findOwners(String filePath) {
        return run(Queries.FIND_OWNERS, Map.of("path", filePath), "login", "score");
    }

    public ToolResult traceDecision(String topic) {
        return run(Queries.TRACE_DECISION, Map.of("topic", topic), "decisionId", "summary", "login");
    }

    public ToolResult findConflicts(int prNumber) {
        return run(Queries.FIND_CONFLICTS, Map.of("number", prNumber), "decisionId", "summary", "path");
    }

    public ToolResult recommendIssues(String login) {
        return run(Queries.RECOMMEND_ISSUES, Map.of("login", login), "number", "title");
    }

    public static String describe(List<Map<String, Object>> rows, String emptyMessage) {
        if (rows.isEmpty()) {
            return emptyMessage;
        }
        List<String> descriptions = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey("login")) {
                descriptions.add(String.valueOf(row.get("login")));
            } else if (row.containsKey("summary")) {
                descriptions.add(String.valueOf(row.get("summary"))
                        + (row.containsKey("path") ? " affecting " + row.get("path") : ""));
            } else if (row.containsKey("title")) {
                descriptions.add("Issue #" + row.get("number") + ": " + row.get("title"));
            } else {
                descriptions.add(row.toString());
            }
        }
        return String.join("; ", descriptions);
    }

    private ToolResult run(String query, Map<String, Object> parameters, String... dataFields) {
        GraphContextGenerator graph = graphClient.projectGraph();
        ResultSet results = graph.query(query, parameters);
        List<Map<String, Object>> data = new ArrayList<>();
        Set<Long> pathNodeIds = new LinkedHashSet<>();
        for (Record record : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String field : dataFields) {
                row.put(field, record.getValue(field));
            }
            data.add(row);
            for (String key : record.keys()) {
                if (key.endsWith("Id")) {
                    Object value = record.getValue(key);
                    if (value instanceof Number number) {
                        pathNodeIds.add(number.longValue());
                    }
                }
            }
        }
        return new ToolResult(data, List.copyOf(pathNodeIds));
    }

    public record ToolResult(List<Map<String, Object>> data, List<Long> pathNodeIds) {
    }
}
