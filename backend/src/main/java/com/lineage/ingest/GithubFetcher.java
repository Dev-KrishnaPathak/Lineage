package com.lineage.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GithubFetcher {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ITEMS = 300;
    private static final int MAX_DETAIL_PULL_REQUESTS_WITHOUT_TOKEN = 30;
    private static final int MAX_DETAIL_PULL_REQUESTS_WITH_TOKEN = 60;

    private static final Logger logger = LoggerFactory.getLogger(GithubFetcher.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
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
        List<JsonNode> issues = pages(canonicalRepository,
                "/issues?state=all&sort=updated&direction=desc");
        issues.removeIf(issue -> issue.has("pull_request"));
        List<JsonNode> pullRequests = pages(canonicalRepository,
                "/pulls?state=all&sort=updated&direction=desc");
        List<JsonNode> reviews = new ArrayList<>();
        List<JsonNode> comments = new ArrayList<>();
        List<JsonNode> files = new ArrayList<>();
        for (JsonNode pullRequest : pullRequests.stream().limit(maxDetailPullRequests()).toList()) {
            int number = pullRequest.path("number").asInt();
            for (JsonNode review : pages(canonicalRepository, "/pulls/" + number + "/reviews")) {
                ObjectNode reviewWithPullRequest = review.deepCopy();
                reviewWithPullRequest.put("number", number);
                reviews.add(reviewWithPullRequest);
            }
            for (JsonNode comment : pages(canonicalRepository, "/issues/" + number + "/comments")) {
                ObjectNode commentWithPullRequest = comment.deepCopy();
                commentWithPullRequest.put("number", number);
                comments.add(commentWithPullRequest);
            }
            for (JsonNode file : pages(canonicalRepository, "/pulls/" + number + "/files")) {
                ObjectNode fileWithPullRequest = file.deepCopy();
                fileWithPullRequest.put("number", number);
                files.add(fileWithPullRequest);
            }
        }
        logger.info("Fetched {} PRs, {} reviews, {} comments and {} files for {}",
                pullRequests.size(), reviews.size(), comments.size(), files.size(), canonicalRepository);
        return new RepositorySnapshot(issues, pullRequests, reviews, comments, files);
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
        for (int page = 1; page <= 3 && result.size() < MAX_ITEMS; page++) {
            String separator = path.contains("?") ? "&" : "?";
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repositoryName + path
                            + separator + "page=" + page + "&per_page=" + PAGE_SIZE))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
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
}
