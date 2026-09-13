package io.databaseradar.java;

import java.nio.file.Path;
import java.util.List;
import io.databaseradar.sql.SqlDialect;

public record ScanOptions(
        Path root,
        int javaVersion,
        List<Path> additionalRoots,
        SqlDialect sqlDialect,
        boolean symbolResolution) {
    public ScanOptions {
        root = root.toAbsolutePath().normalize();
        additionalRoots = additionalRoots == null ? List.of() : additionalRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        sqlDialect = sqlDialect == null ? SqlDialect.AUTO : sqlDialect;
        if (!List.of(8, 11, 17, 21).contains(javaVersion)) {
            throw new IllegalArgumentException("Supported source versions: 8, 11, 17, 21");
        }
    }

    public ScanOptions(Path root, int javaVersion, List<Path> additionalRoots) {
        this(root, javaVersion, additionalRoots, SqlDialect.AUTO, true);
    }

    public ScanOptions(Path root, int javaVersion, List<Path> additionalRoots, SqlDialect sqlDialect) {
        this(root, javaVersion, additionalRoots, sqlDialect, true);
    }

    public static ScanOptions defaults(Path root) {
        return new ScanOptions(root, 8, List.of(), SqlDialect.AUTO, true);
    }
}
