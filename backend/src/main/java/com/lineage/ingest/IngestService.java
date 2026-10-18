package com.lineage.ingest;

import com.lineage.db.GraphClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;

@Service
public class IngestService {

    private final GithubFetcher githubFetcher;
    private final GraphLoader graphLoader;
    private final DecisionDetector decisionDetector;
    private final GraphClient graphClient;
    private volatile IngestStatus status = IngestStatus.idle();

    public IngestService(
            GithubFetcher githubFetcher,
            GraphLoader graphLoader,
            DecisionDetector decisionDetector,
            GraphClient graphClient) {
        this.githubFetcher = githubFetcher;
        this.graphLoader = graphLoader;
        this.decisionDetector = decisionDetector;
        this.graphClient = graphClient;
    }

    public synchronized IngestStatus start(String repository) {
        String canonicalRepository = githubFetcher.normalizeRepository(repository);
        if (status.running()) {
            throw new IllegalStateException("Another repository is already loading");
        }
        status = new IngestStatus(canonicalRepository, 0, "queued", "Preparing GitHub import", true, null);
        CompletableFuture.runAsync(() -> load(canonicalRepository));
        return status;
    }

    public IngestStatus status() {
        return status;
    }

    private void load(String canonicalRepository) {
        try {
            status = new IngestStatus(canonicalRepository, 10, "fetching", "Fetching issues and pull requests", true, null);
            GithubFetcher.RepositorySnapshot snapshot = githubFetcher.fetch(canonicalRepository);
            if (snapshot.issues().isEmpty() && snapshot.pullRequests().isEmpty()) {
                throw new IllegalStateException("GitHub returned no issues or pull requests for " + canonicalRepository);
            }
            status = new IngestStatus(canonicalRepository, 55, "indexing", "Building repository relationships", true, null);
            graphClient.projectGraph().query("MATCH (n) DETACH DELETE n");
            graphLoader.load(snapshot);
            status = new IngestStatus(canonicalRepository, 90, "analyzing", "Detecting repository decisions", true, null);
            decisionDetector.detect(snapshot);
            status = new IngestStatus(canonicalRepository, 100, "complete", "Repository graph is ready", false, null);
        } catch (Exception exception) {
            status = new IngestStatus(canonicalRepository, 0, "failed", null, false, message(exception));
        }
    }

    private String message(Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return "GitHub took too long to respond. Try again or use a GitHub token.";
        }
        if (exception instanceof IOException && exception.getMessage() != null
                && exception.getMessage().toLowerCase().contains("rate limit")) {
            return "GitHub API rate limit reached. Set GITHUB_TOKEN or GH_TOKEN and restart the backend.";
        }
        return exception.getMessage() == null ? "GitHub ingestion failed." : exception.getMessage();
    }

    public record IngestResult(String repository, int issues, int pullRequests, int reviews, int files) {
    }

    public record IngestStatus(String repository, int progress, String phase, String message,
                               boolean running, String error) {
        static IngestStatus idle() {
            return new IngestStatus("", 0, "idle", "Load a GitHub repository to begin", false, null);
        }
    }
}
