package com.lineage.ingest;

import com.lineage.db.GraphClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import redis.clients.jedis.exceptions.JedisException;

@Component
@ConditionalOnProperty(name = "lineage.ingest.enabled", havingValue = "true")
public class IngestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final GithubFetcher githubFetcher;
    private final GraphLoader graphLoader;
    private final DecisionDetector decisionDetector;
    private final GraphClient graphClient;

    public IngestRunner(
            GithubFetcher githubFetcher,
            GraphLoader graphLoader,
            DecisionDetector decisionDetector,
            GraphClient graphClient) {
        this.githubFetcher = githubFetcher;
        this.graphLoader = graphLoader;
        this.decisionDetector = decisionDetector;
        this.graphClient = graphClient;
    }

    @Override
    public void run(String... args) {
        try {
            GithubFetcher.RepositorySnapshot snapshot = githubFetcher.fetch();
            graphLoader.load(snapshot);
            decisionDetector.detect(snapshot);
            log.info("GitHub ingestion completed");
        } catch (JedisException exception) {
            log.error("Could not access FalkorDB at {}:{} during startup ingestion; continuing startup",
                    graphClient.host(), graphClient.port(), exception);
        } catch (IOException | InterruptedException | IllegalArgumentException | IllegalStateException exception) {
            log.warn("GitHub ingestion failed; loading demo data instead");
            try {
                graphLoader.loadDemo();
            } catch (JedisException databaseException) {
                log.error("Could not access FalkorDB at {}:{} during demo fallback; continuing startup",
                        graphClient.host(), graphClient.port(), databaseException);
            }
        }
    }
}
