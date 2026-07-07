package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-305: Lambda 大表查询无 LIMIT
 * <p>
 * 对大表（超过配置阈值）执行 Lambda 查询时，
 * 如果没有 .last("LIMIT N") 或分页调用（.page()），则报告问题。
 * </p>
 *
 * @author marker
 */
public class LambdaNoLimitRule {

    private final long largeTableThreshold;

    public LambdaNoLimitRule() {
        this(10000L);
    }

    public LambdaNoLimitRule(long largeTableThreshold) {
        this.largeTableThreshold = largeTableThreshold;
    }

    /**
     * 检查 Lambda 查询链对大表是否有 LIMIT 保护
     *
     * @param chain  解析好的 Lambda 查询链
     * @param schema Schema 元数据注册表
     * @return 发现的问题列表
     */
    public List<JavaIssue> check(LambdaChain chain, SchemaRegistry schema) {
        List<JavaIssue> issues = new ArrayList<>();
        if (schema == null || chain.getEntityClass() == null) {
            return issues;
        }

        String tableName = schema.resolveEntityToTable(chain.getEntityClass());
        if (tableName == null) {
            return issues;
        }

        String database = chain.getResolvedDatabase();
        long rowCount = schema.getRowCount(tableName, database);
        if (rowCount < largeTableThreshold) {
            return issues; // 小表不检查
        }

        // 如果有 .last() 或分页调用，视为有 LIMIT 保护
        if (chain.isHasLast() || chain.isHasTermination() && hasPagination(chain)) {
            return issues;
        }

        // 无 last() 且无分页 → 报告
        if (!chain.isHasLast()) {
            issues.add(new JavaIssue(
                    "SQL-305",
                    chain.getStartLine(),
                    String.format("大表 '%s'（%d 行）的 Lambda 查询无 LIMIT 保护，可能返回海量数据",
                            tableName, rowCount),
                    "添加 .last(\"LIMIT N\") 或使用分页查询 .page(page)"
            ));
        }
        return issues;
    }

    /**
     * 判断是否有分页调用（page 方法表示分页）
     */
    private boolean hasPagination(LambdaChain chain) {
        return chain.getText() != null && chain.getText().contains(".page(");
    }
}
