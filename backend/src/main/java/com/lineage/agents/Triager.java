package com.lineage.agents;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Triager {

    private final AgentModel model;

    public Triager(AgentModel model) {
        this.model = model;
    }

    public AgentResponse run(int prNumber) {
        String fallback = "Triaged PR #" + prNumber + "; inspect changed files and related topics.";
        String response = model.respond("""
                You are the Triager agent. Summarize the scope and likely topics for pull request #%d.
                Return a concise handoff for the Matcher agent.
                """.formatted(prNumber), fallback);
        return new AgentResponse("Triager", "Matcher", response, List.of());
    }

    public record AgentResponse(String fromAgent, String toAgent, String payload, List<Long> pathNodeIds) {
    }
}
