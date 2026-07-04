package com.yourcompany.sqlreview.rules;

import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

/**
 * SQL Review 规则注册
 *
 * @author marker
 */
public class SqlRulesDefinition implements RulesDefinition {

    public static final String REPO_KEY = "sql-review";
    public static final String REPO_NAME = "SQL Review";
    public static final String LANGUAGE = "java";

    public static final String SQL_001 = "SQL-001";
    public static final String SQL_002 = "SQL-002";
    public static final String SQL_003 = "SQL-003";
    public static final String SQL_004 = "SQL-004";
    public static final String SQL_101 = "SQL-101";
    public static final String SQL_103 = "SQL-103";
    public static final String SQL_201 = "SQL-201";

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(REPO_KEY, LANGUAGE)
                .setName(REPO_NAME);

        // SQL-001: WHERE 条件字段缺少索引
        repository.createRule(SQL_001)
                .setName("WHERE 条件字段缺少索引")
                .setHtmlDescription("WHERE 子句中使用的字段在生产数据库中没有索引，可能导致性能问题。")
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        // SQL-002: 大表全表扫描
        repository.createRule(SQL_002)
                .setName("大表全表扫描")
                .setHtmlDescription("对大表（超过阈值行数）执行无 WHERE 条件或条件无索引的查询。")
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        // SQL-003: SELECT *
        repository.createRule(SQL_003)
                .setName("使用 SELECT *")
                .setHtmlDescription("使用 SELECT * 会返回不必要的列，增加网络传输和内存消耗。")
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        // SQL-004: 索引列使用函数
        repository.createRule(SQL_004)
                .setName("索引列使用函数导致索引失效")
                .setHtmlDescription("在 WHERE 条件中对索引列使用函数会导致索引失效，触发全表扫描。")
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        // SQL-101: 大表查询无 LIMIT
        repository.createRule(SQL_101)
                .setName("大表查询无 LIMIT")
                .setHtmlDescription("对大表执行查询未设置 LIMIT，可能返回过多数据。")
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        // SQL-103: LIKE 以 % 开头
        repository.createRule(SQL_103)
                .setName("LIKE 以 % 开头")
                .setHtmlDescription("LIKE '%xxx' 模式无法使用索引，会导致全表扫描。")
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        // SQL-201: 动态 SQL 拼接
        repository.createRule(SQL_201)
                .setName("动态 SQL 拼接风险")
                .setHtmlDescription("使用 ${} 进行 SQL 拼接存在 SQL 注入风险，应使用 #{} 参数化查询。")
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.VULNERABILITY)
                .setStatus(RuleStatus.READY);

        repository.done();
    }
}
