package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-201: 动态 SQL 拼接风险
 */
public class DynamicConcatRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if (stmt.hasDynamicConcat()) {
            issues.add(new Issue("SQL-201", "MAJOR",
                    "使用 ${} 动态拼接，存在 SQL 注入风险",
                    "使用 #{} 参数化查询替代 ${}"));
        }
        return issues;
    }
}
