package io.databaseradar.graph;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class CanonicalIds {
    private CanonicalIds() {
    }

    public static String file(String relativePath) {
        return "file:" + relativePath.replace('\\', '/');
    }

    public static String resource(String relativePath) {
        return "resource:" + relativePath.replace('\\', '/');
    }

    public static String type(String qualifiedName) {
        return "type:" + qualifiedName;
    }

    public static String entity(String qualifiedName) {
        return "entity:" + qualifiedName;
    }

    public static String field(String qualifiedType, String fieldName) {
        return "field:" + qualifiedType + "#" + fieldName;
    }

    public static String method(String qualifiedType, String name, List<String> parameterTypes) {
        return "method:" + qualifiedType + "#" + name + "(" + String.join(",", parameterTypes) + ")";
    }

    public static String table(String name) {
        return "table:" + normalizeQualifiedName(name);
    }

    public static String column(String table, String column) {
        return "column:" + normalizeQualifiedName(table) + "." + normalizePart(column);
    }

    public static String sql(String sql) {
        String normalized = sql.strip().replaceAll("\\s+", " ");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "sql:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String normalizeQualifiedName(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (char character : input.strip().toCharArray()) {
            if (quote == 0 && (character == '"' || character == '`' || character == '[')) {
                quote = character == '[' ? ']' : character;
                current.append(character);
            } else if (quote != 0 && character == quote) {
                current.append(character);
                quote = 0;
            } else if (quote == 0 && character == '.') {
                parts.add(normalizePart(current.toString()));
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        parts.add(normalizePart(current.toString()));
        return String.join(".", parts);
    }

    public static String normalizePart(String input) {
        String value = input.strip();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '`' && last == '`')
                    || (first == '[' && last == ']')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
