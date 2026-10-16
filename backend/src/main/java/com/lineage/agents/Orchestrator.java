package com.lineage.agents;

import com.lineage.memory.HandoffStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class Orchestrator {

    private final Triager triager;
    private final Matcher matcher;
    private final Historian historian;
    private final HandoffStore handoffStore;

    public Orchestrator(
            Triager triager,
            Matcher matcher,
            Historian historian,
            HandoffStore handoffStore) {
        this.triager = triager;
        this.matcher = matcher;
        this.historian = historian;
        this.handoffStore = handoffStore;
    }

    public ReviewTrail review(int prNumber) {
        Triager.AgentResponse triage = triager.run(prNumber);
        HandoffStore.Handoff first = new HandoffStore.Handoff(
                "pr-" + prNumber + "-triage", triage.fromAgent(), triage.toAgent(),
                triage.payload(), null, prNumber);
        handoffStore.write(first);

        Matcher.AgentResponse matching = matcher.run(prNumber, triage.payload());
        HandoffStore.Handoff second = new HandoffStore.Handoff(
                "pr-" + prNumber + "-match", matching.fromAgent(), matching.toAgent(),
                matching.payload(), first.id(), prNumber);
        handoffStore.write(second);

        Historian.AgentResponse history = historian.run(prNumber, matching.payload());
        HandoffStore.Handoff third = new HandoffStore.Handoff(
                "pr-" + prNumber + "-history", history.fromAgent(), history.toAgent(),
                history.payload(), second.id(), prNumber);
        handoffStore.write(third);

        List<AgentTrailEntry> trail = new ArrayList<>();
        trail.add(new AgentTrailEntry(first.id(), first.fromAgent(), first.toAgent(), first.payload()));
        trail.add(new AgentTrailEntry(second.id(), second.fromAgent(), second.toAgent(), second.payload()));
        trail.add(new AgentTrailEntry(third.id(), third.fromAgent(), third.toAgent(), third.payload()));
        LinkedHashSet<Long> pathNodeIds = new LinkedHashSet<>();
        pathNodeIds.addAll(triage.pathNodeIds());
        pathNodeIds.addAll(matching.pathNodeIds());
        pathNodeIds.addAll(history.pathNodeIds());
        return new ReviewTrail(List.copyOf(trail), List.copyOf(pathNodeIds));
    }

    public record AgentTrailEntry(String id, String fromAgent, String toAgent, String payload) {
    }

    public record ReviewTrail(List<AgentTrailEntry> handoffs, List<Long> pathNodeIds) {
    }
}
