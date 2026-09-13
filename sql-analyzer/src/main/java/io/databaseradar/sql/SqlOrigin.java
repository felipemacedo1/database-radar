package io.databaseradar.sql;

import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.SourceRange;

public record SqlOrigin(
        String ownerNodeId,
        String file,
        SourceRange range,
        String evidenceKind,
        Confidence confidence) {
}
