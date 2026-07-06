package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-101: 大表查询无 LIMIT
 */
public class NoLimitLargeTableRule implements SqlXmlRule {

    private static final long DEFAULT_THRESHOLD = 50000;
    private final long noLimitThreshold;

    public NoLimitLargeTableRule() {
        this(DEFAULT_THRESHOLD);
    }

    public NoLimitLargeTableRule(long noLimitThreshold) {
        this.noLimitThreshold = noLimitThreshold;
    }

    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if (!"SELECT".equals(stmt.getType())) return issues;
        if (stmt.isHasLimit()) return issues;

        for (String table : stmt.getTables()) {
            long rowCount = schema.getRowCount(table);
            if (rowCount > noLimitThreshold) {
                issues.add(new Issue("SQL-101", "MAJOR",
                        String.format("大表 %s（约 %d 行）查询无 LIMIT", table, rowCount),
                        "添加 LIMIT 或使用分页查询"));
                break;
            }
        }
        return issues;
    }
}
