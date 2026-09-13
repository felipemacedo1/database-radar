package io.databaseradar.java;

import java.nio.file.Path;
import java.util.List;

public record ScanOptions(Path root, int javaVersion, List<Path> additionalRoots) {
    public ScanOptions {
        root = root.toAbsolutePath().normalize();
        additionalRoots = additionalRoots == null ? List.of() : additionalRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        if (!List.of(8, 11, 17, 21).contains(javaVersion)) {
            throw new IllegalArgumentException("Supported source versions: 8, 11, 17, 21");
        }
    }

    public static ScanOptions defaults(Path root) {
        return new ScanOptions(root, 8, List.of());
    }
}
