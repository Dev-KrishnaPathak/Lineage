package com.lineage.db;

import com.falkordb.FalkorDB;
import com.falkordb.Driver;
import com.falkordb.GraphContextGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Component
public class GraphClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphClient.class);

    private final Driver driver;
    private final String host;
    private final int port;
    private final String projectGraphName;

    public GraphClient(
            @Value("${lineage.falkordb.host:localhost}") String host,
            @Value("${lineage.falkordb.port:6379}") int port,
            @Value("${lineage.falkordb.user:}") String user,
            @Value("${lineage.falkordb.password:}") String password,
            @Value("${lineage.falkordb.project-graph:lineage_demo}") String projectGraphName) {
        this.host = host;
        this.port = port;
        this.driver = user.isBlank() && password.isBlank()
                ? FalkorDB.driver(host, port)
                : FalkorDB.driver(host, port, user, password);
        this.projectGraphName = Objects.requireNonNull(projectGraphName);
        LOGGER.info("FalkorDB configured at {}:{}", host, port);
    }

    public GraphContextGenerator graph(String graphName) {
        if (graphName == null || !graphName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid graph name");
        }
        return driver.graph(graphName);
    }

    public GraphContextGenerator projectGraph() {
        return graph(projectGraphName);
    }

    public String projectGraphName() {
        return projectGraphName;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }
}
