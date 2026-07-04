package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-103: LIKE 以 % 开头
 */
public class LikeLeadingWildcardRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        String sql = stmt.getSql().toUpperCase();
        if (sql.contains("LIKE '%") || sql.contains("LIKE \"%" )) {
            issues.add(new Issue("SQL-103", "MAJOR",
                    "LIKE 以 % 开头，无法使用索引",
                    "考虑使用全文索引或改为前缀匹配"));
        }
        return issues;
    }
}
