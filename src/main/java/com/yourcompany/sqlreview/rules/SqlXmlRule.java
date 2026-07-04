package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;

import java.util.List;

/**
 * XML 规则接口
 *
 * @author marker
 */
public interface SqlXmlRule {
    List<Issue> check(SqlStatement stmt, SchemaRegistry schema);
}
