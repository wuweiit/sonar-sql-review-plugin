package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-003: SELECT * 检测
 */
public class SelectStarXmlRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        List<Issue> issues = new ArrayList<>();
        if ("SELECT".equals(stmt.getType()) && stmt.isSelectStar()) {
            issues.add(new Issue("SQL-003", "MAJOR",
                    "使用了 SELECT *，应指定具体字段",
                    "将 SELECT * 替换为需要的字段列表"));
        }
        return issues;
    }
}
