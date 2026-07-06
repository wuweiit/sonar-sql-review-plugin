package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL-001: WHERE 条件字段缺少索引
 * <p>
 * 整体分析 WHERE 条件列集合，而非逐列单独报告。
 * 小表（≤10000 行）跳过检查，因为全表扫描代价可忽略。
 * </p>
 */
public class NoIndexWhereRule implements SqlXmlRule {

    private static final long DEFAULT_THRESHOLD = 10000;
    private final long smallTableThreshold;

    public NoIndexWhereRule() {
        this(DEFAULT_THRESHOLD);
    }

    public NoIndexWhereRule(long smallTableThreshold) {
        this.smallTableThreshold = smallTableThreshold;
    }

    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        List<String> condCols = stmt.getConditionColumns();
        if (condCols.isEmpty()) return issues;

        for (String table : stmt.getTables()) {
            // 小表跳过：全表扫描代价可忽略
            long rowCount = schema.getRowCount(table);
            if (rowCount <= smallTableThreshold) continue;

            // 复合索引前缀覆盖检查：整体分析条件列集合
            boolean indexCovered = schema.hasCompositeIndexCoverage(table, condCols);
            if (!indexCovered) {
                String env = schema.getSourceEnv(table);
                String colList = condCols.stream().collect(Collectors.joining(", "));
                issues.add(new Issue("SQL-001", "CRITICAL",
                        String.format("表 %s（约 %d 行, %s）WHERE 条件 [%s] 无法命中任何索引",
                                table, rowCount, env, colList),
                        String.format("为 WHERE 条件字段添加索引，或调整条件以利用现有索引的最左前缀")));
            }
        }
        return issues;
    }
}
