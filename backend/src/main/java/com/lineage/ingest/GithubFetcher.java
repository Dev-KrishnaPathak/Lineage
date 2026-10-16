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
import java.util.ArrayList;
import java.util.List;

@Component
public class GithubFetcher {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ITEMS = 300;

    private final HttpClient httpClient = HttpClient.newHttpClient();
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
        if (!repository.matches("[^/]+/[^/]+")) {
            throw new IllegalStateException("lineage.github.repository must be owner/repository");
        }
        List<JsonNode> issues = pages("/issues?state=all&sort=updated");
        List<JsonNode> pullRequests = pages("/pulls?state=all&sort=updated");
        List<JsonNode> reviews = new ArrayList<>();
        List<JsonNode> comments = new ArrayList<>();
        List<JsonNode> files = new ArrayList<>();
        for (JsonNode pullRequest : pullRequests) {
            int number = pullRequest.path("number").asInt();
            reviews.addAll(pages("/pulls/" + number + "/reviews"));
            comments.addAll(pages("/issues/" + number + "/comments"));
            files.addAll(pages("/pulls/" + number + "/files"));
        }
        return new RepositorySnapshot(issues, pullRequests, reviews, comments, files);
    }

    private List<JsonNode> pages(String path) throws IOException, InterruptedException {
        List<JsonNode> result = new ArrayList<>();
        for (int page = 1; page <= 3 && result.size() < MAX_ITEMS; page++) {
            String separator = path.contains("?") ? "&" : "?";
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repository + path
                            + separator + "page=" + page + "&per_page=" + PAGE_SIZE))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (!token.isBlank()) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                throw new IOException("GitHub API rate limit reached");
            }
            if (response.statusCode() >= 400) {
                throw new IOException("GitHub API request failed: " + response.statusCode());
            }
            JsonNode pageItems = objectMapper.readTree(response.body());
            if (!pageItems.isArray() || pageItems.isEmpty()) {
                break;
            }
            pageItems.forEach(result::add);
        }
        return result.size() > MAX_ITEMS ? result.subList(0, MAX_ITEMS) : result;
    }

    public record RepositorySnapshot(
            List<JsonNode> issues,
            List<JsonNode> pullRequests,
            List<JsonNode> reviews,
            List<JsonNode> comments,
            List<JsonNode> files) {
    }
}
