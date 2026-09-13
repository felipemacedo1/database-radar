package io.databaseradar.sql;

import java.util.List;

public record SqlAnalysis(
        boolean parsed,
        SqlOperation operation,
        List<SqlTableAccess> tables,
        List<SqlColumnAccess> columns,
        List<String> ambiguousColumns,
        String error) {

    public SqlAnalysis {
        tables = List.copyOf(tables);
        columns = List.copyOf(columns);
        ambiguousColumns = List.copyOf(ambiguousColumns);
    }
}
