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
        if (!status.done()) {
            throw new IllegalStateException("Another repository is already loading");
        }
        status = IngestStatus.started(canonicalRepository);
        CompletableFuture.runAsync(() -> load(canonicalRepository));
        return new IngestStatus(canonicalRepository, "started", Counts.empty(), false, null);
    }

    public IngestStatus status() {
        return status;
    }

    private void load(String canonicalRepository) {
        try {
            status = new IngestStatus(canonicalRepository, "fetching", Counts.empty(), false, null);
            GithubFetcher.RepositorySnapshot snapshot = githubFetcher.fetch(canonicalRepository);
            if (snapshot.issues().isEmpty() && snapshot.pullRequests().isEmpty()) {
                throw new IllegalStateException("GitHub returned no issues or pull requests for " + canonicalRepository);
            }
            Counts counts = new Counts(
                    snapshot.issues().size(),
                    snapshot.pullRequests().size(),
                    snapshot.reviews().size(),
                    snapshot.comments().size(),
                    snapshot.files().size());
            status = new IngestStatus(canonicalRepository, "indexing", counts, false, null);
            graphClient.projectGraph().query("MATCH (n) DETACH DELETE n");
            graphLoader.load(snapshot);
            status = new IngestStatus(canonicalRepository, "analyzing", counts, false, null);
            decisionDetector.detect(snapshot);
            status = new IngestStatus(canonicalRepository, "complete", counts, true, null);
        } catch (IOException | InterruptedException | IllegalArgumentException | IllegalStateException exception) {
            status = new IngestStatus(canonicalRepository, "failed", Counts.empty(), true, message(exception));
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

    public record IngestStart(String status) {
    }

    public record IngestStatus(String repository, String stage, Counts counts,
                               boolean done, String error) {
        static IngestStatus started(String repository) {
            return new IngestStatus(repository, "queued", Counts.empty(), false, null);
        }

        static IngestStatus idle() {
            return new IngestStatus("", "idle", Counts.empty(), true, null);
        }
    }

    public record Counts(int issues, int pullRequests, int reviews, int comments, int files) {
        static Counts empty() {
            return new Counts(0, 0, 0, 0, 0);
        }
    }
}
