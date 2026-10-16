package com.lineage.api;

import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.lineage.agents.Orchestrator;
import com.lineage.baseline.RagBaseline;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import com.lineage.tools.GraphTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class ApiController {

    private final GraphClient graphClient;
    private final GraphTools graphTools;
    private final Orchestrator orchestrator;
    private final RagBaseline ragBaseline;

    public ApiController(
            GraphClient graphClient,
            GraphTools graphTools,
            Orchestrator orchestrator,
            RagBaseline ragBaseline) {
        this.graphClient = graphClient;
        this.graphTools = graphTools;
        this.orchestrator = orchestrator;
        this.ragBaseline = ragBaseline;
    }

    @GetMapping("/graph")
    public GraphResponse graph() {
        List<GraphNode> nodes = new ArrayList<>();
        ResultSet nodeResults = graphClient.projectGraph().query(Queries.GRAPH_NODES);
        for (Record record : nodeResults) {
            nodes.add(new GraphNode(
                    number(record.getValue("id")).longValue(),
                    String.valueOf((Object) record.getValue("label")),
                    firstLabel(record.getValue("labels"))));
        }
        List<GraphLink> links = new ArrayList<>();
        ResultSet linkResults = graphClient.projectGraph().query(Queries.GRAPH_LINKS);
        for (Record record : linkResults) {
            links.add(new GraphLink(
                    number(record.getValue("source")).longValue(),
                    number(record.getValue("target")).longValue(),
                    String.valueOf((Object) record.getValue("type"))));
        }
        return new GraphResponse(nodes, links);
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        GraphTools.ToolResult result;
        String question = request.question().toLowerCase();
        if (question.contains("decision") || question.contains("decided")) {
            result = graphTools.traceDecision(lastWord(request.question()));
        } else if (question.contains("conflict")) {
            result = graphTools.findConflicts(extractNumber(request.question()));
        } else {
            result = graphTools.recommendIssues("demo-user");
        }
        return new AskResponse(result.data().toString(), result.pathNodeIds(), result.data());
    }

    @PostMapping("/pr")
    public PrResponse pr(@RequestBody PrRequest request) {
        GraphTools.ToolResult reviewers = graphTools.suggestReviewers(request.prNumber());
        GraphTools.ToolResult conflicts = graphTools.findConflicts(request.prNumber());
        Orchestrator.ReviewTrail trail = orchestrator.review(request.prNumber());
        return new PrResponse(
                reviewers.data(),
                conflicts.data(),
                trail.handoffs().isEmpty() ? "" : trail.handoffs().get(trail.handoffs().size() - 1).payload(),
                trail.handoffs(),
                mergeIds(reviewers.pathNodeIds(), conflicts.pathNodeIds(), trail.pathNodeIds()));
    }

    @GetMapping("/recommend/{login}")
    public RecommendationResponse recommend(@PathVariable String login) {
        GraphTools.ToolResult result = graphTools.recommendIssues(login);
        return new RecommendationResponse(result.data(), result.pathNodeIds());
    }

    @PostMapping("/ask/baseline")
    public BaselineResponse baseline(@RequestBody AskRequest request) {
        return new BaselineResponse(ragBaseline.answer(request.question()));
    }

    @PostMapping("/simulate/{nodeId}")
    public SimulationResponse simulate(@PathVariable long nodeId) {
        ResultSet results = graphClient.projectGraph().query(
                Queries.SIMULATE_DOWNSTREAM, Map.of("nodeId", nodeId));
        List<Long> affected = new ArrayList<>();
        for (Record record : results) {
            affected.add(number(record.getValue("id")).longValue());
        }
        return new SimulationResponse(affected);
    }

    private List<Long> mergeIds(List<Long>... paths) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (List<Long> path : paths) {
            ids.addAll(path);
        }
        return List.copyOf(ids);
    }

    private static Number number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Expected numeric graph ID");
        }
        return number;
    }

    private static String firstLabel(Object value) {
        if (value instanceof List<?> labels && !labels.isEmpty()) {
            return String.valueOf(labels.get(0));
        }
        return "Unknown";
    }

    private static String lastWord(String value) {
        String[] words = value.trim().split("\\s+");
        return words.length == 0 ? value : words[words.length - 1].replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static int extractNumber(String value) {
        String digits = value.replaceAll("\\D+", " ").trim();
        return digits.isBlank() ? 0 : Integer.parseInt(digits.split("\\s+")[0]);
    }

    public record GraphResponse(List<GraphNode> nodes, List<GraphLink> links) {
    }

    public record GraphNode(long id, String label, String type) {
    }

    public record GraphLink(long source, long target, String type) {
    }

    public record AskRequest(String question) {
    }

    public record AskResponse(String answer, List<Long> pathNodeIds, Object sources) {
    }

    public record PrRequest(int prNumber) {
    }

    public record PrResponse(
            Object reviewers,
            Object conflicts,
            String draftComment,
            List<Orchestrator.AgentTrailEntry> handoffs,
            List<Long> pathNodeIds) {
    }

    public record RecommendationResponse(Object issues, List<Long> pathNodeIds) {
    }

    public record BaselineResponse(String answer) {
    }

    public record SimulationResponse(List<Long> affectedNodeIds) {
    }
}
