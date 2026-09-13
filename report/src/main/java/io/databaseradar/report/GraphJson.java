package io.databaseradar.report;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.databaseradar.graph.GraphDocument;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class GraphJson {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public void write(GraphDocument document, Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, normalized.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), document);
            try {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public GraphDocument read(Path input) throws IOException {
        GraphDocument document = mapper.readValue(input.toFile(), GraphDocument.class);
        if (!document.schemaVersion().startsWith("1.")) {
            throw new IOException("Unsupported graph schema: " + document.schemaVersion());
        }
        return document;
    }

    public String toJson(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
