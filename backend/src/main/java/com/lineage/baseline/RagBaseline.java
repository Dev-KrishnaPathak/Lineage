package com.lineage.baseline;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RagBaseline {

    private final EmbeddingModel embeddingModel;
    private final List<Document> documents = new ArrayList<>();

    public RagBaseline(@Value("${lineage.llm.api-key:}") String apiKey) {
        this.embeddingModel = apiKey.isBlank() ? null : OpenAiEmbeddingModel.builder().apiKey(apiKey).build();
        documents.add(new Document("Issue #1", "Improve caching. Cache API responses for faster reads."));
        documents.add(new Document("PR #2", "Add cache layer. Adds a cache layer to the API."));
    }

    public synchronized void index(String source, String text) {
        documents.add(new Document(source, text));
    }

    public synchronized String answer(String question) {
        if (documents.isEmpty()) {
            return "No baseline documents are indexed.";
        }
        List<Float> queryVector = vector(question);
        Document best = documents.stream()
                .max(Comparator.comparingDouble(document -> cosine(queryVector, vector(document.text()))))
                .orElseThrow();
        return "Based on " + best.source() + ": " + best.text();
    }

    private List<Float> vector(String text) {
        if (embeddingModel != null) {
            return embeddingModel.embed(text).content().vectorAsList();
        }
        float[] counts = new float[128];
        for (String token : tokens(text)) {
            counts[Math.floorMod(token.hashCode(), counts.length)] += 1.0f;
        }
        List<Float> vector = new ArrayList<>(counts.length);
        for (float value : counts) {
            vector.add(value);
        }
        return vector;
    }

    private double cosine(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0;
        double leftMagnitude = 0;
        double rightMagnitude = 0;
        for (int index = 0; index < size; index++) {
            dot += left.get(index) * right.get(index);
            leftMagnitude += left.get(index) * left.get(index);
            rightMagnitude += right.get(index) * right.get(index);
        }
        return leftMagnitude == 0 || rightMagnitude == 0
                ? 0
                : dot / Math.sqrt(leftMagnitude * rightMagnitude);
    }

    private List<String> tokens(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).split("\\W+"));
    }

    private record Document(String source, String text) {
    }
}
