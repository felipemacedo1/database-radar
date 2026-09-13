package io.databaseradar.spike;

public record SpikeFinding(
        String method,
        String edge,
        String table,
        String file,
        int line,
        String confidence) {
}
