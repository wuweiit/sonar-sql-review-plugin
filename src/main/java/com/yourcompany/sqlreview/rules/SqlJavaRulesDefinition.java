package com.yourcompany.sqlreview.rules;

import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

/**
 * SQL Review Java 规则注册（MyBatis Lambda 表达式检查）
 * <p>
 * 注册两类规则：
 * <ol>
 *   <li>SQL-001 ~ SQL-201：从 Lambda 转换为 SQL 后，复用 XML 侧的所有规则</li>
 *   <li>SQL-301 ~ SQL-306：Lambda 特有的直接检查规则</li>
 * </ol>
 * </p>
 *
 * @author marker
 */
public class SqlJavaRulesDefinition implements RulesDefinition {

    public static final String JAVA_REPO_KEY = "sql-review-java";
    public static final String JAVA_REPO_NAME = "SQL Review (Java)";
    public static final String LANGUAGE = "java";

    // ===== 从 Lambda → SQL 转换后复用的规则 =====

    /** WHERE 条件字段缺少索引 */
    public static final String SQL_001 = "SQL-001";
    /** 大表全表扫描 */
    public static final String SQL_002 = "SQL-002";
    /** SELECT * */
    public static final String SQL_003 = "SQL-003";
    /** 索引列使用函数导致索引失效 */
    public static final String SQL_004 = "SQL-004";
    /** 大表查询无 LIMIT */
    public static final String SQL_101 = "SQL-101";
    /** LIKE 以 % 开头 */
    public static final String SQL_103 = "SQL-103";
    /** 动态 SQL 拼接风险 */
    public static final String SQL_201 = "SQL-201";

    // ===== Lambda 特有规则 =====

    /** Lambda selectAll() 等价于 SELECT * */
    public static final String SQL_301 = "SQL-301";
    /** Lambda 查询缺少 WHERE 条件 */
    public static final String SQL_302 = "SQL-302";
    /** Lambda WHERE 条件字段缺少索引 */
    public static final String SQL_303 = "SQL-303";
    /** Lambda LIKE 以 % 开头 */
    public static final String SQL_304 = "SQL-304";
    /** Lambda 大表查询无 LIMIT */
    public static final String SQL_305 = "SQL-305";
    /** Lambda .apply() 中索引列使用函数导致索引失效 */
    public static final String SQL_306 = "SQL-306";

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(JAVA_REPO_KEY, LANGUAGE)
                .setName(JAVA_REPO_NAME);

        // ===== Lambda → SQL 复用规则 =====

        repository.createRule(SQL_001)
                .setName("WHERE 条件字段缺少索引")
                .setHtmlDescription(loadHtml("SQL-001"))
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_002)
                .setName("大表全表扫描")
                .setHtmlDescription(loadHtml("SQL-002"))
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_003)
                .setName("使用 SELECT *")
                .setHtmlDescription(loadHtml("SQL-003"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_004)
                .setName("索引列使用函数导致索引失效")
                .setHtmlDescription(loadHtml("SQL-004"))
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_101)
                .setName("大表查询无 LIMIT")
                .setHtmlDescription(loadHtml("SQL-101"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_103)
                .setName("LIKE 以 % 开头")
                .setHtmlDescription(loadHtml("SQL-103"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_201)
                .setName("动态 SQL 拼接风险")
                .setHtmlDescription(loadHtml("SQL-201"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.VULNERABILITY)
                .setStatus(RuleStatus.READY);

        // ===== Lambda 特有规则 =====

        repository.createRule(SQL_301)
                .setName("Lambda selectAll() 等价于 SELECT *")
                .setHtmlDescription(loadHtml("SQL-301"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_302)
                .setName("Lambda 查询缺少 WHERE 条件")
                .setHtmlDescription(loadHtml("SQL-302"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_303)
                .setName("Lambda WHERE 条件字段缺少索引")
                .setHtmlDescription(loadHtml("SQL-303"))
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_304)
                .setName("Lambda LIKE 以 % 开头")
                .setHtmlDescription(loadHtml("SQL-304"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.CODE_SMELL)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_305)
                .setName("Lambda 大表查询无 LIMIT")
                .setHtmlDescription(loadHtml("SQL-305"))
                .setSeverity(Severity.MAJOR)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.createRule(SQL_306)
                .setName("Lambda .apply() 中索引列使用函数导致索引失效")
                .setHtmlDescription(loadHtml("SQL-306"))
                .setSeverity(Severity.CRITICAL)
                .setType(RuleType.BUG)
                .setStatus(RuleStatus.READY);

        repository.done();
    }

    private String loadHtml(String ruleKey) {
        try (var is = getClass().getResourceAsStream(
                "/org/sonar/l10n/sqlreview/rules/" + ruleKey + ".html")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // fallback
        }
        return ruleKey + " 规则描述";
    }
}
