package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL-306: Lambda .apply() 中对索引列使用函数导致索引失效
 * <p>
 * 检测 .apply("YEAR(create_time) = {0}", year) 等原始 SQL 片段中
 * 对索引列使用函数（YEAR/MONTH/DAY/DATE/UPPER/LOWER 等）导致索引失效的情况。
 * </p>
 *
 * @author marker
 */
public class LambdaApplyFunctionRule {

    /** 匹配 SQL 函数调用：YEAR(col)、DATE(col)、UPPER(col) 等 */
    private static final Pattern FUNC_ON_COL = Pattern.compile(
            "(?i)(?:YEAR|MONTH|DAY|DATE|HOUR|MINUTE|SECOND|UPPER|LOWER|TRIM|SUBSTRING|CONCAT|CAST|CONVERT)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * 检查 Lambda 查询链中 .apply() 的 SQL 片段是否对索引列使用了函数
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
        if (tableName == null || schema.getTable(tableName) == null) {
            return issues;
        }

        for (LambdaChain.ApplyInfo applyInfo : chain.getApplyCalls()) {
            String sqlFragment = applyInfo.getSqlFragment();
            Matcher matcher = FUNC_ON_COL.matcher(sqlFragment);
            while (matcher.find()) {
                String col = matcher.group(1).toLowerCase();
                if (schema.hasIndex(tableName, col)) {
                    issues.add(new JavaIssue(
                            "SQL-306",
                            applyInfo.getLine(),
                            String.format(".apply() 中索引列 '%s'（表 %s）使用函数导致索引失效",
                                    col, tableName),
                            "将函数移到等号右侧，或使用日期范围查询替代函数"
                    ));
                }
            }
        }
        return issues;
    }
}
