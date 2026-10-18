package com.lineage.db;

import com.falkordb.GraphContextGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(1)
public class GraphIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphIndexInitializer.class);

    private final GraphClient graphClient;

    public GraphIndexInitializer(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        GraphContextGenerator graph = graphClient.projectGraph();
        for (String indexQuery : Queries.PROJECT_INDEXES) {
            try {
                graph.query(indexQuery);
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                if (message == null || !message.toLowerCase().contains("already indexed")) {
                    throw exception;
                }
                log.debug("Index already exists: {}", indexQuery);
            }
        }
    }
}
