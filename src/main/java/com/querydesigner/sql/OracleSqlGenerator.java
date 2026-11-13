package com.querydesigner.sql;

import static com.querydesigner.sql.OracleIdentifiers.quote;

import com.querydesigner.model.Aggregate;
import com.querydesigner.model.ColumnSpec;
import com.querydesigner.model.JoinDef;
import com.querydesigner.model.JoinType;
import com.querydesigner.model.QueryModel;
import com.querydesigner.model.SortType;
import com.querydesigner.model.TableRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Converts a {@link QueryModel} into an Oracle (12c+) SELECT statement. */
public class OracleSqlGenerator {

    /** Oracle 12.1 limits identifiers to 30 bytes; keep generated aliases within that. */
    private static final int MAX_ALIAS_LENGTH = 30;

    private static final Pattern STARTS_WITH_OPERATOR = Pattern.compile(
            "^(=|<>|!=|<=|>=|<|>|(?i:(NOT\\s+)?(LIKE|IN|BETWEEN)\\b)|(?i:IS\\b))");
    private static final Pattern CONTAINS_OR = Pattern.compile("(?i)\\bOR\\b");

    public String generate(QueryModel model) {
        return generate(model, 0);
    }

    /** @param maxRows when &gt; 0 appends {@code FETCH FIRST n ROWS ONLY} (used for previews) */
    public String generate(QueryModel model, int maxRows) {
        if (model.getTables().isEmpty()) {
            return "";
        }
        List<ColumnSpec> cols = model.getColumns();
        boolean grouping = cols.stream().anyMatch(c -> c.getAggregate() != Aggregate.NONE);

        StringBuilder sql = new StringBuilder("SELECT ");
        if (model.isDistinct()) {
            sql.append("DISTINCT ");
        }
        sql.append(selectList(cols));
        sql.append("\nFROM ").append(fromClause(model));

        List<String> where = new ArrayList<>();
        List<String> having = new ArrayList<>();
        for (ColumnSpec c : cols) {
            if (c.getFilter().isEmpty()) {
                continue;
            }
            if (c.getAggregate().isFunction()) {
                having.add(condition(expression(c), c.getFilter()));
            } else if (!c.isStar()) {
                where.add(condition(columnRef(c), c.getFilter()));
            }
        }
        if (!where.isEmpty()) {
            sql.append("\nWHERE ").append(String.join("\n  AND ", where));
        }

        if (grouping) {
            Set<String> groupBy = new LinkedHashSet<>();
            for (ColumnSpec c : cols) {
                if (c.isStar()) {
                    continue;
                }
                if (c.getAggregate() == Aggregate.GROUP_BY
                        || (c.getAggregate() == Aggregate.NONE && (c.isOutput() || c.getSortType() != SortType.NONE))) {
                    groupBy.add(columnRef(c));
                }
            }
            if (!groupBy.isEmpty()) {
                sql.append("\nGROUP BY ").append(String.join(", ", groupBy));
            }
        }
        if (!having.isEmpty()) {
            sql.append("\nHAVING ").append(String.join("\n   AND ", having));
        }

        List<ColumnSpec> sorted = new ArrayList<>();
        for (ColumnSpec c : cols) {
            if (c.getSortType() != SortType.NONE) {
                sorted.add(c);
            }
        }
        if (!sorted.isEmpty()) {
            sorted.sort(Comparator.comparingInt(c -> c.getSortOrder() <= 0 ? Integer.MAX_VALUE : c.getSortOrder()));
            sql.append("\nORDER BY ").append(sorted.stream()
                    .map(c -> expression(c) + (c.getSortType() == SortType.DESC ? " DESC" : ""))
                    .collect(Collectors.joining(", ")));
        }

        if (maxRows > 0) {
            sql.append("\nFETCH FIRST ").append(maxRows).append(" ROWS ONLY");
        }
        return sql.toString();
    }

    private String selectList(List<ColumnSpec> cols) {
        List<String> items = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (ColumnSpec c : cols) {
            if (c.isOutput() && !c.getAlias().isEmpty()) {
                used.add(c.getAlias().toUpperCase(Locale.ROOT));
            }
        }
        for (ColumnSpec c : cols) {
            if (!c.isOutput()) {
                continue;
            }
            String item = expression(c);
            String alias = c.getAlias();
            if (alias.isEmpty() && c.getAggregate().isFunction()) {
                alias = uniqueAlias(defaultAlias(c), used);
            }
            if (!alias.isEmpty()) {
                item += " AS " + quote(alias);
            }
            items.add(item);
        }
        return items.isEmpty() ? "*" : String.join(",\n       ", items);
    }

    private String fromClause(QueryModel model) {
        List<TableRef> tables = model.getTables();
        List<JoinDef> remaining = new ArrayList<>(model.getJoins());
        Set<TableRef> placed = new LinkedHashSet<>();
        StringBuilder from = new StringBuilder(tableSql(tables.get(0)));
        placed.add(tables.get(0));

        while (placed.size() < tables.size()) {
            boolean progress = true;
            while (progress) {
                progress = false;
                for (TableRef t : tables) {
                    if (placed.contains(t)) {
                        continue;
                    }
                    List<JoinDef> links = remaining.stream()
                            .filter(j -> j.involves(t) && placed.contains(j.other(t)))
                            .toList();
                    if (links.isEmpty()) {
                        continue;
                    }
                    JoinDef first = links.get(0);
                    // The newly placed table is written on the right side of "... JOIN t".
                    JoinType type = first.getLeft() == t ? first.getType().flipped() : first.getType();
                    from.append("\n").append(type.sql()).append(' ').append(tableSql(t))
                            .append(" ON ").append(links.stream().map(this::joinCondition)
                                    .collect(Collectors.joining(" AND ")));
                    remaining.removeAll(links);
                    placed.add(t);
                    progress = true;
                    break;
                }
            }
            // Tables not connected by any join: cartesian product.
            for (TableRef t : tables) {
                if (!placed.contains(t)) {
                    from.append("\nCROSS JOIN ").append(tableSql(t));
                    placed.add(t);
                    break;
                }
            }
        }
        return from.toString();
    }

    private String tableSql(TableRef t) {
        String name = (t.getSchema() == null || t.getSchema().isEmpty() ? "" : quote(t.getSchema()) + ".")
                + quote(t.getName());
        // Oracle does not accept AS before a table alias.
        return t.hasCustomAlias() ? name + " " + quote(t.getAlias()) : name;
    }

    private String joinCondition(JoinDef j) {
        return quote(j.getLeft().getAlias()) + "." + quote(j.getLeftColumn())
                + " = " + quote(j.getRight().getAlias()) + "." + quote(j.getRightColumn());
    }

    private String columnRef(ColumnSpec c) {
        if (c.isStar()) {
            return "*";
        }
        return quote(c.getTable().getAlias()) + "." + quote(c.getColumn());
    }

    private String expression(ColumnSpec c) {
        String ref = columnRef(c);
        Aggregate a = c.getAggregate();
        if (!a.isFunction()) {
            return ref;
        }
        if (a == Aggregate.COUNT_DISTINCT) {
            return "COUNT(DISTINCT " + ref + ")";
        }
        return a.function() + "(" + ref + ")";
    }

    /** Filter text like {@code > 100}, {@code LIKE 'A%'}, {@code IN (1,2)} or a bare value (means "="). */
    static String condition(String expr, String filter) {
        String f = filter.trim();
        String cond = STARTS_WITH_OPERATOR.matcher(f).find() ? expr + " " + f : expr + " = " + f;
        return CONTAINS_OR.matcher(f).find() ? "(" + cond + ")" : cond;
    }

    private static String defaultAlias(ColumnSpec c) {
        String fn = c.getAggregate() == Aggregate.COUNT_DISTINCT ? "COUNT_DISTINCT" : c.getAggregate().function();
        String col = c.isStar() ? "ALL" : c.getColumn().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        String alias = fn + "_" + col;
        return alias.length() > MAX_ALIAS_LENGTH ? alias.substring(0, MAX_ALIAS_LENGTH) : alias;
    }

    private static String uniqueAlias(String base, Set<String> used) {
        String alias = base;
        int n = 2;
        while (used.contains(alias)) {
            String suffix = "_" + n++;
            alias = (base.length() + suffix.length() > MAX_ALIAS_LENGTH
                    ? base.substring(0, MAX_ALIAS_LENGTH - suffix.length()) : base) + suffix;
        }
        used.add(alias);
        return alias;
    }
}
