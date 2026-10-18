package com.lineage.ingest;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final GraphLoader graphLoader;

    public DemoDataInitializer(GraphLoader graphLoader) {
        this.graphLoader = graphLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        graphLoader.loadDemo();
    }
}
