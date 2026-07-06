package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-002: 大表全表扫描（无 WHERE 条件）
 * <p>
 * 仅检查大表且无 WHERE 条件的情况。
 * 有 WHERE 但无索引的场景由 SQL-001 负责。
 * </p>
 */
public class FullTableScanRule implements SqlXmlRule {

    private static final long DEFAULT_THRESHOLD = 10000;
    private final long largeTableThreshold;

    public FullTableScanRule() {
        this(DEFAULT_THRESHOLD);
    }

    public FullTableScanRule(long largeTableThreshold) {
        this.largeTableThreshold = largeTableThreshold;
    }

    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if (!"SELECT".equals(stmt.getType())) return issues;

        for (String table : stmt.getTables()) {
            long rowCount = schema.getRowCount(table);
            if (rowCount <= largeTableThreshold) continue;

            if (stmt.getConditionColumns().isEmpty()) {
                issues.add(new Issue("SQL-002", "CRITICAL",
                        String.format("大表 %s（约 %d 行）全表扫描，无 WHERE 条件", table, rowCount),
                        "添加 WHERE 条件限制查询范围"));
            }
        }
        return issues;
    }
}
