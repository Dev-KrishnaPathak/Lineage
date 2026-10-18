package com.lineage.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class GithubFetcher {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ITEMS = 300;
    private static final int MAX_DETAIL_PULL_REQUESTS_WITHOUT_TOKEN = 30;
    private static final int MAX_DETAIL_PULL_REQUESTS_WITH_TOKEN = 60;
    private static final int DETAIL_FETCH_THREADS = 6;
    private static final int MAX_PAGES = 3;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String GITHUB_API_BASE_URL = "https://api.github.com/repos/";
    private static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String ISSUES_PATH = "/issues?state=all&sort=updated&direction=desc";
    private static final String PULL_REQUESTS_PATH = "/pulls?state=all&sort=updated&direction=desc";

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubFetcher.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper;
    private final String token;
    private final String repository;

    public GithubFetcher(
            ObjectMapper objectMapper,
            @Value("${lineage.github.token:}") String token,
            @Value("${lineage.github.repository:}") String repository) {
        this.objectMapper = objectMapper;
        this.token = token;
        this.repository = repository;
    }

    public RepositorySnapshot fetch() throws IOException, InterruptedException {
        return fetch(repository);
    }

    public RepositorySnapshot fetch(String repositoryName) throws IOException, InterruptedException {
        String canonicalRepository = normalizeRepository(repositoryName);
        List<JsonNode> issues = pages(canonicalRepository, ISSUES_PATH);
        issues.removeIf(issue -> issue.has("pull_request"));
        List<JsonNode> pullRequests = pages(canonicalRepository, PULL_REQUESTS_PATH);
        List<JsonNode> reviews = new ArrayList<>();
        List<JsonNode> comments = new ArrayList<>();
        List<JsonNode> files = new ArrayList<>();
        List<Future<PullRequestDetails>> detailTasks = new ArrayList<>();
        ExecutorService detailExecutor = Executors.newFixedThreadPool(DETAIL_FETCH_THREADS);
        try {
            for (JsonNode pullRequest : pullRequests.stream().limit(maxDetailPullRequests()).toList()) {
                detailTasks.add(detailExecutor.submit(() -> fetchPullRequestDetails(canonicalRepository, pullRequest)));
            }
            for (Future<PullRequestDetails> detailTask : detailTasks) {
                PullRequestDetails details = getDetails(detailTask);
                reviews.addAll(details.reviews());
                comments.addAll(details.comments());
                files.addAll(details.files());
            }
        } finally {
            detailExecutor.shutdownNow();
        }
        LOGGER.info("Fetched {} PRs, {} reviews, {} comments and {} files for {}",
                pullRequests.size(), reviews.size(), comments.size(), files.size(), canonicalRepository);
        return new RepositorySnapshot(issues, pullRequests, reviews, comments, files);
    }

    private PullRequestDetails fetchPullRequestDetails(
            String repositoryName,
            JsonNode pullRequest) throws IOException, InterruptedException {
        int pullRequestNumber = pullRequest.path("number").asInt();
        List<JsonNode> reviews = new ArrayList<>();
        List<JsonNode> comments = new ArrayList<>();
        List<JsonNode> files = new ArrayList<>();
        appendTaggedNodes(reviews, pages(repositoryName,
                "/pulls/" + pullRequestNumber + "/reviews"), pullRequestNumber);
        appendTaggedNodes(comments, pages(repositoryName,
                "/issues/" + pullRequestNumber + "/comments"), pullRequestNumber);
        appendTaggedNodes(files, pages(repositoryName,
                "/pulls/" + pullRequestNumber + "/files"), pullRequestNumber);
        return new PullRequestDetails(reviews, comments, files);
    }

    private PullRequestDetails getDetails(Future<PullRequestDetails> detailTask)
            throws IOException, InterruptedException {
        try {
            return detailTask.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("GitHub pull request details could not be fetched", cause);
        }
    }

    private void appendTaggedNodes(
            List<JsonNode> destination,
            List<JsonNode> nodes,
            int pullRequestNumber) {
        for (JsonNode node : nodes) {
            ObjectNode taggedNode = node.deepCopy();
            taggedNode.put("number", pullRequestNumber);
            destination.add(taggedNode);
        }
    }

    private int maxDetailPullRequests() {
        return token.isBlank()
                ? MAX_DETAIL_PULL_REQUESTS_WITHOUT_TOKEN
                : MAX_DETAIL_PULL_REQUESTS_WITH_TOKEN;
    }

    public String normalizeRepository(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a GitHub repository URL or owner/repository");
        }
        String candidate = value.trim();
        if (candidate.matches("https?://(www\\.)?github\\.com/.*")) {
            candidate = candidate.replaceFirst("https?://(www\\.)?github\\.com/", "");
        }
        candidate = candidate.split("[?#]", 2)[0].replaceAll("^/+|/+$", "").replaceFirst("\\.git$", "");
        if (!candidate.matches("[^/]+/[^/]+")) {
            throw new IllegalArgumentException("Use a GitHub URL like https://github.com/owner/repository");
        }
        return candidate;
    }

    private List<JsonNode> pages(String repositoryName, String path) throws IOException, InterruptedException {
        List<JsonNode> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES && result.size() < MAX_ITEMS; page++) {
            String separator = path.contains("?") ? "&" : "?";
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE_URL + repositoryName + path
                            + separator + "page=" + page + "&per_page=" + PAGE_SIZE))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", GITHUB_ACCEPT_HEADER)
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION);
            if (!token.isBlank()) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || isRateLimited(response)) {
                throw new IOException("GitHub API rate limit reached. Set GITHUB_TOKEN or GH_TOKEN and restart the backend.");
            }
            if (response.statusCode() >= 400) {
                String detail = objectMapper.readTree(response.body()).path("message").asText("HTTP " + response.statusCode());
                throw new IOException("GitHub API request failed: " + detail);
            }
            JsonNode pageItems = objectMapper.readTree(response.body());
            if (!pageItems.isArray() || pageItems.isEmpty()) {
                break;
            }
            pageItems.forEach(result::add);
        }
        return result.size() > MAX_ITEMS ? result.subList(0, MAX_ITEMS) : result;
    }

    private boolean isRateLimited(HttpResponse<String> response) {
        return response.statusCode() == 403
                && "0".equals(response.headers().firstValue("X-RateLimit-Remaining").orElse(""));
    }

    public record RepositorySnapshot(
            List<JsonNode> issues,
            List<JsonNode> pullRequests,
            List<JsonNode> reviews,
            List<JsonNode> comments,
            List<JsonNode> files) {
    }

    private record PullRequestDetails(
            List<JsonNode> reviews,
            List<JsonNode> comments,
            List<JsonNode> files) {
    }
}
