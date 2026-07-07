package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL-004: 索引列使用函数导致索引失效
 */
public class IndexFunctionRule implements SqlXmlRule {

    private static final Pattern FUNC_ON_COL = Pattern.compile(
            "(?i)(?:YEAR|MONTH|DAY|DATE|UPPER|LOWER|TRIM|SUBSTRING|CONCAT|CAST|CONVERT)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if (!"SELECT".equals(stmt.getType())) return issues;

        String sql = stmt.getSql();
        int whereIdx = sql.toUpperCase().indexOf("WHERE");
        if (whereIdx < 0) return issues;

        String whereClause = sql.substring(whereIdx);
        Matcher matcher = FUNC_ON_COL.matcher(whereClause);
        while (matcher.find()) {
            String col = matcher.group(1).toLowerCase();
            for (String table : stmt.getTables()) {
                if (schema.hasIndex(table, col, stmt.getDatabase())) {
                    issues.add(new Issue("SQL-004", "CRITICAL",
                            String.format("索引列 %s.%s 使用函数导致索引失效", table, col),
                            "将函数移到等号右侧，或使用计算列"));
                }
            }
        }
        return issues;
    }
}
