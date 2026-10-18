package com.lineage.ingest;

import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.lineage.db.GraphClient;
import com.lineage.db.Queries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final GraphClient graphClient;
    private final GraphLoader graphLoader;
    private final DecisionDetector decisionDetector;
    private final GithubFetcher githubFetcher;
    private final String repository;
    private final String githubToken;

    public DemoDataInitializer(
            GraphClient graphClient,
            GraphLoader graphLoader,
            DecisionDetector decisionDetector,
            GithubFetcher githubFetcher,
            @Value("${lineage.github.repository:}") String repository,
            @Value("${lineage.github.token:}") String githubToken) {
        this.graphClient = graphClient;
        this.graphLoader = graphLoader;
        this.decisionDetector = decisionDetector;
        this.githubFetcher = githubFetcher;
        this.repository = repository;
        this.githubToken = githubToken;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (nodeCount() > 0) {
            LOGGER.info("Startup graph already contains data; skipping seed");
            return;
        }
        if (!repository.isBlank() && !githubToken.isBlank()) {
            try {
                loadRepository();
                LOGGER.info("Startup graph loaded from configured repository");
                return;
            } catch (java.io.IOException | InterruptedException | IllegalStateException exception) {
                LOGGER.warn("Configured repository ingest failed; loading demo seed");
            }
        }
        graphLoader.loadDemo();
        LOGGER.info("Startup graph loaded from demo seed");
    }

    private long nodeCount() {
        ResultSet results = graphClient.projectGraph().query(Queries.GRAPH_NODE_COUNT);
        for (Record record : results) {
            Object count = record.getValue("count");
            if (count instanceof Number number) {
                return number.longValue();
            }
        }
        return 0;
    }

    private void loadRepository() throws java.io.IOException, InterruptedException {
        GithubFetcher.RepositorySnapshot snapshot = githubFetcher.fetch(repository);
        if (snapshot.issues().isEmpty() && snapshot.pullRequests().isEmpty()) {
            throw new IllegalStateException("GitHub returned no issues or pull requests");
        }
        graphLoader.load(snapshot);
        decisionDetector.detect(snapshot);
    }
}
