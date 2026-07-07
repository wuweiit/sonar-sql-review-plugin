package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.util.NameConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * SQL-303: Lambda 查询 WHERE 条件字段缺少索引
 * <p>
 * 整体分析 WHERE 条件列集合（复合索引前缀覆盖），而非逐列单独报告。
 * 小表（≤10000 行）跳过检查。
 * </p>
 *
 * @author marker
 */
public class LambdaNoIndexRule {

    private static final long DEFAULT_THRESHOLD = 10000;
    private final long smallTableThreshold;

    public LambdaNoIndexRule() {
        this(DEFAULT_THRESHOLD);
    }

    public LambdaNoIndexRule(long smallTableThreshold) {
        this.smallTableThreshold = smallTableThreshold;
    }

    public List<JavaIssue> check(LambdaChain chain, SchemaRegistry schema) {
        List<JavaIssue> issues = new ArrayList<>();
        if (schema == null) {
            return issues;
        }

        String database = chain != null ? chain.getResolvedDatabase() : null;

        // 按表分组收集条件（跳过不需要索引检查的方法）
        Map<String, List<LambdaChain.ConditionInfo>> tableConditions = new HashMap<>();
        for (LambdaChain.ConditionInfo cond : chain.getConditions()) {
            if ("isNull".equals(cond.getMethod()) || "isNotNull".equals(cond.getMethod())
                    || "orderByDesc".equals(cond.getMethod()) || "orderByAsc".equals(cond.getMethod())) {
                continue;
            }
            String tableName = resolveCondTable(cond, chain, schema);
            if (tableName == null || schema.getTable(tableName, database) == null) continue;

            // 小表跳过
            if (schema.getRowCount(tableName, database) <= smallTableThreshold) continue;

            tableConditions.computeIfAbsent(tableName, k -> new ArrayList<>()).add(cond);
        }

        // 按表检查复合索引覆盖
        for (Map.Entry<String, List<LambdaChain.ConditionInfo>> entry : tableConditions.entrySet()) {
            String tableName = entry.getKey();
            List<LambdaChain.ConditionInfo> conds = entry.getValue();

            // 收集所有条件列名
            List<String> colNames = new ArrayList<>();
            Set<String> seenCols = new HashSet<>();
            for (LambdaChain.ConditionInfo c : conds) {
                if (seenCols.add(c.getColumnName())) {
                    colNames.add(c.getColumnName());
                }
            }

            // 复合索引前缀覆盖检查
            if (schema.hasCompositeIndexCoverage(tableName, colNames, database)) {
                continue; // 索引可用，不报告
            }

            // 无法命中索引 → 报告每个无索引列（精确到代码行）
            for (LambdaChain.ConditionInfo cond : conds) {
                if (!schema.hasIndex(tableName, cond.getColumnName(), database)) {
                    issues.add(new JavaIssue(
                            "SQL-303",
                            cond.getLine(),
                            String.format("Lambda 条件字段 '%s'（表 %s）缺少索引，可能导致全表扫描",
                                    cond.getColumnName(), tableName),
                            String.format("在表 %s 的 '%s' 列上创建索引", tableName, cond.getColumnName())
                    ));
                }
            }
        }
        return issues;
    }

    /**
     * 解析条件所属的表名
     * 优先使用条件自身的 entityClass，回退到链主表的 entityClass
     */
    private String resolveCondTable(LambdaChain.ConditionInfo cond, LambdaChain chain, SchemaRegistry schema) {
        String entityClass = cond.getEntityClass();
        if (entityClass == null) {
            entityClass = chain.getEntityClass();
        }
        if (entityClass == null) {
            return null;
        }
        String table = schema.resolveEntityToTable(entityClass);
        if (table == null) {
            table = NameConverter.entityClassToTable(entityClass);
        }
        return table;
    }
}
