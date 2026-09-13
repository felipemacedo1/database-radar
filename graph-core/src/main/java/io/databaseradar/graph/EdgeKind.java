package io.databaseradar.graph;

public enum EdgeKind {
    DECLARES,
    CALLS,
    CONTAINS_SQL,
    READS_TABLE,
    WRITES_TABLE,
    READS_COLUMN,
    WRITES_COLUMN,
    MAPS_TO_TABLE,
    MAPS_TO_COLUMN,
    LOADS_RESOURCE,
    POSSIBLY_CALLS
}
