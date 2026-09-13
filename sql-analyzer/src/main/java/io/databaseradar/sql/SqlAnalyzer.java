package io.databaseradar.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.databaseradar.graph.CanonicalIds.normalizePart;
import static io.databaseradar.graph.CanonicalIds.normalizeQualifiedName;

public final class SqlAnalyzer {
    public SqlAnalysis analyze(String sql) {
        try {
            return analyzeStatement(CCJSqlParserUtil.parse(sql));
        } catch (JSQLParserException | RuntimeException exception) {
            return new SqlAnalysis(false, SqlOperation.UNSUPPORTED, List.of(), List.of(), List.of(),
                    boundedMessage(exception));
        }
    }

    SqlAnalysis analyzeStatement(Statement statement) {
        StructureCollector collector = new StructureCollector();
        collector.getTables(statement);

        SqlOperation operation = operation(statement);
        Table target = writeTarget(statement);
        String targetName = target == null ? null : normalizeQualifiedName(target.getFullyQualifiedName());
        Map<String, String> aliases = aliasMap(collector.tables);
        Set<String> allTables = collector.tables.stream()
                .map(Table::getFullyQualifiedName)
                .map(SqlAnalyzer::safeNormalizeQualifiedName)
                .filter(name -> !name.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (targetName != null) {
            allTables.add(targetName);
        }

        Set<SqlTableAccess> tableAccesses = new LinkedHashSet<>();
        if (targetName != null) {
            tableAccesses.add(new SqlTableAccess(targetName, AccessMode.WRITE));
        }
        if (operation == SqlOperation.SELECT) {
            allTables.forEach(table -> tableAccesses.add(new SqlTableAccess(table, AccessMode.READ)));
        } else {
            allTables.stream()
                    .filter(table -> !table.equals(targetName))
                    .forEach(table -> tableAccesses.add(new SqlTableAccess(table, AccessMode.READ)));
        }

        Set<String> writtenColumns = writtenColumns(statement);
        Set<SqlColumnAccess> columnAccesses = new LinkedHashSet<>();
        if (targetName != null) {
            writtenColumns.forEach(column ->
                    columnAccesses.add(new SqlColumnAccess(targetName, column, AccessMode.WRITE)));
        }

        List<String> ambiguous = new ArrayList<>();
        for (Column column : collector.columns) {
            String name = normalizePart(column.getColumnName());
            if (writtenColumns.contains(name) && isUnqualified(column)) {
                continue;
            }
            String owner = resolveOwner(column, aliases, allTables);
            if (owner == null) {
                ambiguous.add(column.getFullyQualifiedName());
            } else {
                columnAccesses.add(new SqlColumnAccess(owner, name, AccessMode.READ));
            }
        }

        return new SqlAnalysis(
                true,
                operation,
                sorted(tableAccesses, Comparator.comparing(SqlTableAccess::table)
                        .thenComparing(access -> access.mode().name())),
                sorted(columnAccesses, Comparator.comparing(SqlColumnAccess::table)
                        .thenComparing(SqlColumnAccess::column)
                        .thenComparing(access -> access.mode().name())),
                ambiguous.stream().distinct().sorted().toList(),
                null);
    }

    private static SqlOperation operation(Statement statement) {
        if (statement instanceof Select) {
            return SqlOperation.SELECT;
        }
        if (statement instanceof Insert) {
            return SqlOperation.INSERT;
        }
        if (statement instanceof Update) {
            return SqlOperation.UPDATE;
        }
        if (statement instanceof Delete) {
            return SqlOperation.DELETE;
        }
        return SqlOperation.UNSUPPORTED;
    }

    private static Table writeTarget(Statement statement) {
        if (statement instanceof Insert insert) {
            return insert.getTable();
        }
        if (statement instanceof Update update) {
            return update.getTable();
        }
        if (statement instanceof Delete delete) {
            return delete.getTable();
        }
        return null;
    }

    private static Set<String> writtenColumns(Statement statement) {
        Set<String> columns = new LinkedHashSet<>();
        if (statement instanceof Insert insert && insert.getColumns() != null) {
            insert.getColumns().forEach(column -> columns.add(normalizePart(column.getColumnName())));
        } else if (statement instanceof Update update && update.getUpdateSets() != null) {
            for (UpdateSet updateSet : update.getUpdateSets()) {
                if (updateSet.getColumns() != null) {
                    updateSet.getColumns().forEach(column -> columns.add(normalizePart(column.getColumnName())));
                }
            }
        }
        return columns;
    }

    private static Map<String, String> aliasMap(Collection<Table> tables) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Table table : tables) {
            String canonical = safeNormalizeQualifiedName(table.getFullyQualifiedName());
            aliases.put(table.getName().toUpperCase(Locale.ROOT), canonical);
            aliases.put(canonical.toUpperCase(Locale.ROOT), canonical);
            if (table.getAlias() != null) {
                aliases.put(table.getAlias().getName().toUpperCase(Locale.ROOT), canonical);
            }
        }
        return aliases;
    }

    private static String resolveOwner(Column column, Map<String, String> aliases, Set<String> tables) {
        if (!isUnqualified(column)) {
            String tableName = column.getTable().getName();
            return aliases.getOrDefault(tableName.toUpperCase(Locale.ROOT),
                    safeNormalizeQualifiedName(column.getTable().getFullyQualifiedName()));
        }
        return tables.size() == 1 ? tables.iterator().next() : null;
    }

    private static boolean isUnqualified(Column column) {
        return column.getTable() == null
                || column.getTable().getName() == null
                || column.getTable().getName().isBlank();
    }

    private static String safeNormalizeQualifiedName(String value) {
        return value == null ? "" : normalizeQualifiedName(value);
    }

    private static String boundedMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return exception.getClass().getSimpleName();
        }
        String oneLine = message.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 400 ? oneLine : oneLine.substring(0, 400) + "...";
    }

    private static <T> List<T> sorted(Collection<T> values, Comparator<T> comparator) {
        return values.stream().sorted(comparator).toList();
    }

    private static final class StructureCollector extends TablesNamesFinder<Void> {
        private final Set<Table> tables = new LinkedHashSet<>();
        private final Set<Column> columns = new LinkedHashSet<>();

        @Override
        public <S> Void visit(Table table, S context) {
            tables.add(table);
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(Column column, S context) {
            columns.add(column);
            return super.visit(column, context);
        }
    }
}
