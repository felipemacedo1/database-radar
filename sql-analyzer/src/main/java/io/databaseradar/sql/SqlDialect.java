package io.databaseradar.sql;

import java.util.Locale;

public enum SqlDialect {
    AUTO,
    SQL_SERVER,
    POSTGRESQL,
    MYSQL,
    ANSI;

    public static SqlDialect fromCli(String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "auto" -> AUTO;
            case "sql-server", "sqlserver", "mssql", "t-sql", "tsql" -> SQL_SERVER;
            case "postgresql", "postgres" -> POSTGRESQL;
            case "mysql", "mariadb" -> MYSQL;
            case "ansi" -> ANSI;
            default -> throw new IllegalArgumentException(
                    "SQL dialect must be auto, sql-server, postgresql, mysql, or ansi");
        };
    }
}
