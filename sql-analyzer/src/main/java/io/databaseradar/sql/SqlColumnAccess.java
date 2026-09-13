package io.databaseradar.sql;

public record SqlColumnAccess(String table, String column, AccessMode mode) {
}
