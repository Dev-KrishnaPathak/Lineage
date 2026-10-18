package com.lineage.api;

import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.lineage.agents.Orchestrator;
import com.lineage.agents.AgentModel;
import com.lineage.baseline.RagBaseline;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import com.lineage.ingest.IngestService;
import com.lineage.tools.GraphTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    private static final Pattern PR_NUMBER_PATTERN = Pattern.compile(
            "(?i)\\b(?:pull\\s+request|pr)\\s*#?\\s*(\\d+)\\b|\\b(\\d+)\\b");

    private final GraphClient graphClient;
    private final GraphTools graphTools;
    private final Orchestrator orchestrator;
    private final RagBaseline ragBaseline;
    private final AgentModel agentModel;
    private final IngestService ingestService;

    public ApiController(
            GraphClient graphClient,
            GraphTools graphTools,
            Orchestrator orchestrator,
            RagBaseline ragBaseline,
            AgentModel agentModel,
            IngestService ingestService) {
        this.graphClient = graphClient;
        this.graphTools = graphTools;
        this.orchestrator = orchestrator;
        this.ragBaseline = ragBaseline;
        this.agentModel = agentModel;
        this.ingestService = ingestService;
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

    @PostMapping("/ingest")
    public IngestService.IngestStatus ingest(@RequestBody IngestRequest request) {
        if (request == null || request.repository() == null || request.repository().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a GitHub repository URL or owner/repository");
        }
        try {
            return ingestService.start(request.repository());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @GetMapping("/ingest/status")
    public IngestService.IngestStatus ingestStatus() {
        return ingestService.status();
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        requireQuestion(request);
        GraphTools.ToolResult result;
        String rawQuestion = request.question();
        String question = rawQuestion.toLowerCase();
        int prNumber = extractNumber(rawQuestion);
        logger.info("Ask question=\"{}\", extractedPrNumber={}, graph={}",
                rawQuestion, prNumber, graphClient.projectGraphName());

        if (question.contains("who reviewed") || question.contains("which reviewers")) {
            if (prNumber == 0) {
                return new AskResponse(
                        "Could not find a PR number in your question",
                        List.of(),
                        List.of());
            }
            GraphTools.ToolResult pullRequest = graphTools.findPullRequest(prNumber);
            GraphTools.ToolResult reviewers = graphTools.findReviewers(prNumber);
            String title = pullRequest.data().isEmpty()
                    ? ""
                    : String.valueOf(pullRequest.data().get(0).get("title"));
            String answer = pullRequest.data().isEmpty()
                    ? "Pull request #" + prNumber + " is not present in the loaded graph."
                    : reviewers.data().isEmpty()
                            ? "Pull request #" + prNumber + " (" + title
                                    + ") was found, but no reviewers are recorded."
                            : "Pull request #" + prNumber + " (" + title
                                    + ") was reviewed by " + readableRows(reviewers.data()) + ".";
            return new AskResponse(
                    answer,
                    mergeIds(pullRequest.pathNodeIds(), reviewers.pathNodeIds()),
                    Map.of("pullRequest", pullRequest.data(), "reviewers", reviewers.data()));
        } else if (question.contains("review") || question.contains("reviewer")) {
            if (prNumber == 0) {
                return new AskResponse(
                        "Could not find a PR number in your question",
                        List.of(),
                        List.of());
            }
            result = graphTools.suggestReviewers(prNumber);
        } else if (question.contains("decision") || question.contains("decided")) {
            result = graphTools.traceDecision(extractTopic(request.question()));
        } else if (question.contains("conflict")) {
            if (prNumber == 0) {
                return new AskResponse(
                        "Could not find a PR number in your question",
                        List.of(),
                        List.of());
            }
            result = graphTools.findConflicts(extractNumber(request.question()));
        } else {
            result = graphTools.recommendIssues("demo-user");
        }
        String fallback = readableAnswer(result, request.question());
        String answer = agentModel.respond("""
                Answer the user's repository question in two concise sentences.
                Use only the graph evidence provided. Mention the relevant people, files, decisions, or issues.
                Question: %s
                Graph evidence: %s
                """.formatted(request.question(), result.data()), fallback);
        return new AskResponse(answer, result.pathNodeIds(), result.data());
    }

    @PostMapping("/pr")
    public PrResponse pr(@RequestBody PrRequest request) {
        if (request == null || request.prNumber() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid pull request number is required");
        }
        GraphTools.ToolResult pullRequest = graphTools.findPullRequest(request.prNumber());
        if (pullRequest.data().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pull request #" + request.prNumber() + " is not present in the loaded graph");
        }
        GraphTools.ToolResult reviewers = graphTools.suggestReviewers(request.prNumber());
        GraphTools.ToolResult conflicts = graphTools.findConflicts(request.prNumber());
        Orchestrator.ReviewTrail trail = orchestrator.review(request.prNumber());
        String draftFallback = conflicts.data().isEmpty()
                ? "No recorded decision conflicts were found for this pull request."
                : "Review the recorded decision before merging: " + readableRows(conflicts.data());
        String draftComment = agentModel.respond("""
                Draft a concise, constructive pull request review comment.
                Mention reviewers when present and clearly flag any decision conflict.
                Pull request #%d. Suggested reviewers: %s. Conflicts: %s.
                """.formatted(request.prNumber(), reviewers.data(), conflicts.data()), draftFallback);
        return new PrResponse(
                readableRows(reviewers.data()),
                readableRows(conflicts.data()),
                draftComment,
                trail.handoffs(),
                mergeIds(pullRequest.pathNodeIds(), reviewers.pathNodeIds(), conflicts.pathNodeIds(),
                        trail.pathNodeIds()));
    }

    @GetMapping("/recommend/{login}")
    public RecommendationResponse recommend(@PathVariable String login) {
        GraphTools.ToolResult result = graphTools.recommendIssues(login);
        return new RecommendationResponse(result.data(), result.pathNodeIds());
    }

    @PostMapping("/ask/baseline")
    public BaselineResponse baseline(@RequestBody AskRequest request) {
        requireQuestion(request);
        return new BaselineResponse(ragBaseline.answer(request.question()));
    }

    private static void requireQuestion(AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
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

    @GetMapping("/node/{nodeId}")
    public NodeDetails nodeDetails(@PathVariable long nodeId) {
        ResultSet results = graphClient.projectGraph().query(
                Queries.NODE_DETAILS, Map.of("nodeId", nodeId));
        for (Record record : results) {
            return new NodeDetails(
                    firstLabel(record.getValue("labels")),
                    text(record.getValue("login")),
                    text(record.getValue("name")),
                    text(record.getValue("number")),
                    text(record.getValue("title")),
                    text(record.getValue("body")),
                    text(record.getValue("state")),
                    text(record.getValue("path")),
                    text(record.getValue("url")),
                    text(record.getValue("summary")),
                    text(record.getValue("sourceUrl")),
                    text(record.getValue("fromAgent")),
                    text(record.getValue("toAgent")),
                    text(record.getValue("payload")));
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Graph node was not found");
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private static String extractTopic(String value) {
        String[] words = value.toLowerCase().replaceAll("[^a-z0-9_-]", " ").trim().split("\\s+");
        for (int index = words.length - 1; index >= 0; index--) {
            String word = words[index];
            if (!Set.of("decision", "decided", "why", "was", "the", "a", "an", "about", "for").contains(word)
                    && !word.isBlank()) {
                return word;
            }
        }
        return lastWord(value);
    }

    private static int extractNumber(String value) {
        Matcher matcher = PR_NUMBER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return Integer.parseInt(number);
    }

    private static String readableAnswer(GraphTools.ToolResult result, String question) {
        if (result.data().isEmpty()) {
            if (question.toLowerCase().contains("review")) {
                int number = extractNumber(question);
                return number > 0
                        ? "Pull request #" + number + " is not present in the loaded graph, or it has no recorded reviewer edges. Reload the repository to import more GitHub history, then use a PR number shown in the graph."
                        : "No reviewer evidence is connected to that pull request yet. Reload the repository so changed-file and review relationships are indexed.";
            }
            return "I could not find connected graph evidence for that question.";
        }
        return "For \"" + question + "\", the graph found: " + readableRows(result.data()) + ".";
    }

    private static String readableRows(List<Map<String, Object>> rows) {
        List<String> sentences = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey("login")) {
                String person = String.valueOf(row.get("login"))
                        + (row.containsKey("score") ? " (" + row.get("score") + " shared paths)" : "");
                if (row.containsKey("summary")) {
                    person += " connected to the decision: " + row.get("summary");
                }
                sentences.add(person);
            } else if (row.containsKey("summary")) {
                sentences.add(String.valueOf(row.get("summary"))
                        + (row.containsKey("path") ? " affecting " + row.get("path") : ""));
            } else if (row.containsKey("title")) {
                sentences.add("Issue #" + row.get("number") + ": " + row.get("title"));
            } else {
                sentences.add(row.toString());
            }
        }
        return String.join("; ", sentences);
    }

    public record GraphResponse(List<GraphNode> nodes, List<GraphLink> links) {
    }

    public record GraphNode(long id, String label, String type) {
    }

    public record GraphLink(long source, long target, String type) {
    }

    public record AskRequest(String question) {
    }

    public record IngestRequest(String repository) {
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

    public record NodeDetails(
            String type, String login, String name, String number, String title,
            String body, String state, String path, String url, String summary,
            String sourceUrl, String fromAgent, String toAgent, String payload) {
    }
}
