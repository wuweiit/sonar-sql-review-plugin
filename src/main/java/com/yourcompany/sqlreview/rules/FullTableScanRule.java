package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-002: 大表全表扫描
 */
public class FullTableScanRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if (!"SELECT".equals(stmt.getType())) return issues;

        for (String table : stmt.getTables()) {
            long rowCount = schema.getRowCount(table);
            if (rowCount <= 10000) continue;

            if (stmt.getConditionColumns().isEmpty()) {
                issues.add(new Issue("SQL-002", "CRITICAL",
                        String.format("大表 %s（约 %d 行）全表扫描，无 WHERE 条件", table, rowCount),
                        "添加 WHERE 条件限制查询范围"));
            } else {
                boolean allNoIndex = stmt.getConditionColumns().stream()
                        .allMatch(col -> !schema.hasIndex(table, col));
                if (allNoIndex) {
                    String env = schema.getSourceEnv(table);
                    issues.add(new Issue("SQL-002", "CRITICAL",
                            String.format("大表 %s（约 %d 行, %s）WHERE 条件均无索引，可能全表扫描",
                                    table, rowCount, env),
                            "为 WHERE 条件字段添加索引"));
                }
            }
        }
        return issues;
    }
}
