package com.lineage.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lineage.ingest.enabled", havingValue = "true")
public class IngestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final GithubFetcher githubFetcher;
    private final GraphLoader graphLoader;
    private final DecisionDetector decisionDetector;

    public IngestRunner(
            GithubFetcher githubFetcher,
            GraphLoader graphLoader,
            DecisionDetector decisionDetector) {
        this.githubFetcher = githubFetcher;
        this.graphLoader = graphLoader;
        this.decisionDetector = decisionDetector;
    }

    @Override
    public void run(String... args) {
        try {
            GithubFetcher.RepositorySnapshot snapshot = githubFetcher.fetch();
            graphLoader.load(snapshot);
            decisionDetector.detect(snapshot);
            log.info("GitHub ingestion completed");
        } catch (Exception exception) {
            log.warn("GitHub ingestion failed; loading demo data instead: {}", exception.getMessage());
            graphLoader.loadDemo();
        }
    }
}
