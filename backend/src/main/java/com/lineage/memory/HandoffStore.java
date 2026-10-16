package com.lineage.memory;

import com.falkordb.GraphContextGenerator;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class HandoffStore {

    private final GraphClient graphClient;

    public HandoffStore(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public void write(Handoff handoff) {
        GraphContextGenerator graph = graphClient.projectGraph();
        graph.query(Queries.MERGE_HANDOFF, Map.of(
                "id", handoff.id(),
                "fromAgent", handoff.fromAgent(),
                "toAgent", handoff.toAgent(),
                "payload", handoff.payload(),
                "createdAt", handoff.createdAt()));
        if (handoff.previousId() != null && !handoff.previousId().isBlank()) {
            graph.query(Queries.LINK_HANDOFF_NEXT, Map.of(
                    "previousId", handoff.previousId(), "nextId", handoff.id()));
        }
        graph.query(Queries.LINK_HANDOFF_REGARDING, Map.of(
                "handoffId", handoff.id(), "number", handoff.regardingNumber()));
    }

    public record Handoff(
            String id,
            String fromAgent,
            String toAgent,
            String payload,
            String previousId,
            int regardingNumber,
            String createdAt) {

        public Handoff(
                String id,
                String fromAgent,
                String toAgent,
                String payload,
                String previousId,
                int regardingNumber) {
            this(id, fromAgent, toAgent, payload, previousId, regardingNumber, Instant.now().toString());
        }
    }
}
