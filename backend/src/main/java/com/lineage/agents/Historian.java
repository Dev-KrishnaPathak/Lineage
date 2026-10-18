package com.lineage.agents;

import com.lineage.tools.GraphTools;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Historian {

    private final GraphTools graphTools;
    private final AgentModel model;

    public Historian(GraphTools graphTools, AgentModel model) {
        this.graphTools = graphTools;
        this.model = model;
    }

    public AgentResponse run(int prNumber, String matcher) {
        GraphTools.ToolResult result = graphTools.findConflicts(prNumber);
        String fallback = result.data().isEmpty()
                ? "No recorded decision conflicts were found."
                : "Potential decision conflict: " + GraphTools.describe(result.data(), "");
        String response = model.respond("""
                You are the Historian agent. Check this PR for conflicts with past decisions.
                Draft a concise review comment using the graph result.
                Matcher handoff: %s
                Graph result: %s
                """.formatted(matcher, result.data()), fallback);
        return new AgentResponse("Historian", "Review", response, result.pathNodeIds());
    }

    public record AgentResponse(String fromAgent, String toAgent, String payload, List<Long> pathNodeIds) {
    }
}
