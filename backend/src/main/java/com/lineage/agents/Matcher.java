package com.lineage.agents;

import com.lineage.tools.GraphTools;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Matcher {

    private final GraphTools graphTools;
    private final AgentModel model;

    public Matcher(GraphTools graphTools, AgentModel model) {
        this.graphTools = graphTools;
        this.model = model;
    }

    public AgentResponse run(int prNumber, String triage) {
        GraphTools.ToolResult result = graphTools.suggestReviewers(prNumber);
        String fallback = "Suggested reviewers: " + GraphTools.describe(result.data(), "No reviewer history was found.");
        String response = model.respond("""
                You are the Matcher agent. Based on this graph result, recommend reviewers for PR #%d.
                Triage: %s
                Graph result: %s
                """.formatted(prNumber, triage, result.data()), fallback);
        return new AgentResponse("Matcher", "Historian", response, result.pathNodeIds());
    }

    public record AgentResponse(String fromAgent, String toAgent, String payload, List<Long> pathNodeIds) {
    }
}
