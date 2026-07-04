package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-001: WHERE 条件字段缺少索引
 */
public class NoIndexWhereRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        for (String table : stmt.getTables()) {
            for (String col : stmt.getConditionColumns()) {
                if (!schema.hasIndex(table, col)) {
                    String env = schema.getSourceEnv(table);
                    issues.add(new Issue("SQL-001", "CRITICAL",
                            String.format("WHERE 条件字段 %s.%s 在表 %s（约 %d 行, %s）上无索引",
                                    table, col, table, schema.getRowCount(table), env),
                            String.format("为 %s.%s 添加索引", table, col)));
                }
            }
        }
        return issues;
    }
}
