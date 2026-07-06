package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.rules.Issue;
import com.yourcompany.sqlreview.rules.LikeLeadingWildcardRule;
import com.yourcompany.sqlreview.rules.NoIndexWhereRule;
import com.yourcompany.sqlreview.rules.NoLimitLargeTableRule;
import com.yourcompany.sqlreview.rules.SelectStarXmlRule;
import com.yourcompany.sqlreview.rules.SqlXmlRule;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaToSqlConverter 单元测试
 * 验证 Lambda → SQL 转换后能复用现有 SQL 规则
 *
 * @author marker
 */
class LambdaToSqlConverterTest {

    private LambdaChainParser parser;
    private SchemaRegistry schema;

    @BeforeEach
    void setUp() {
        parser = new LambdaChainParser();
        schema = TestSchemaHelper.loadTestSchema();
    }

    // ===== 单表转换测试 =====

    @Test
    void shouldConvertSimpleEqQuery() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getType()).isEqualTo("SELECT");
        assertThat(stmt.getSql()).contains("SELECT *");
        assertThat(stmt.getSql()).contains("FROM app_user");
        assertThat(stmt.getSql()).contains("WHERE status = ?");
        assertThat(stmt.getTables()).containsExactly("app_user");
        assertThat(stmt.getConditionColumns()).containsExactly("status");
    }

    @Test
    void shouldDetectSelectStar() {
        // 未指定 select() 时默认 selectAll
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.isSelectStar()).isTrue();

        // 复用 SelectStarXmlRule (SQL-003)
        List<Issue> issues = new SelectStarXmlRule().check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-003");
    }

    @Test
    void shouldDetectSelectWithSpecificColumns() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .select(AppUserEntity::getUserId, AppUserEntity::getUserName)",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.isSelectStar()).isFalse();

        // SQL-003 不应报告
        List<Issue> issues = new SelectStarXmlRule().check(stmt, schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectNoWhereCondition() {
        // 无条件查询
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getSql()).doesNotContain("WHERE");
        assertThat(stmt.getConditionColumns()).isEmpty();
    }

    @Test
    void shouldDetectNoLimitOnLargeTable() {
        // app_user 表 150000 行
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.isHasLimit()).isFalse();

        // 复用 NoLimitLargeTableRule (SQL-101)
        List<Issue> issues = new NoLimitLargeTableRule().check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-101");
    }

    @Test
    void shouldDetectLimitWhenLastIsUsed() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .last(\"LIMIT 10\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.isHasLimit()).isTrue();
        assertThat(stmt.getSql()).contains("LIMIT");

        // SQL-101 不应报告
        List<Issue> issues = new NoLimitLargeTableRule().check(stmt, schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectMissingIndexOnCondition() {
        // app_user.create_time 不是任何索引的最左列
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);

        // 复用 NoIndexWhereRule (SQL-001)
        List<Issue> issues = new NoIndexWhereRule().check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-001");
        assertThat(issues.get(0).getMessage()).contains("create_time");
    }

    @Test
    void shouldDetectLikeLeadingWildcard() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .like(AppUserEntity::getUserName, \"%张\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);

        // 复用 LikeLeadingWildcardRule (SQL-103)
        List<Issue> issues = new LikeLeadingWildcardRule().check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-103");
    }

    // ===== 多条件测试 =====

    @Test
    void shouldConvertMultipleConditions() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .like(AppUserEntity::getUserName, \"张%\")",
            "    .ge(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getSql()).contains("status = ?");
        assertThat(stmt.getSql()).contains("user_name LIKE");  // LIKE 保留字面值
        assertThat(stmt.getSql()).contains("create_time >= ?");
        assertThat(stmt.getConditionColumns()).containsExactlyInAnyOrder("status", "user_name", "create_time");
    }

    // ===== MPJ JOIN 测试 =====

    @Test
    void shouldConvertMpjJoinQuery() {
        String[] lines = {
            "new MPJLambdaWrapper<AppArticleEntity>()",
            "    .select(AppArticleEntity::getId, AppArticleEntity::getTitle)",
            "    .leftJoin(AppArticleCategoryEntity.class, AppArticleCategoryEntity::getArticleId, AppArticleEntity::getId)",
            "    .eq(AppArticleEntity::getStatus, 1)",
            "    .eq(AppArticleCategoryEntity::getCategoryId, 5)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        LambdaChain chain = chains.get(0);
        assertThat(chain.getEntityClass()).isEqualTo("AppArticleEntity");
        assertThat(chain.getJoins()).hasSize(1);
        assertThat(chain.getJoins().get(0).getJoinType()).isEqualTo("LEFT");
        assertThat(chain.getJoins().get(0).getEntityClass()).isEqualTo("AppArticleCategoryEntity");

        SqlStatement stmt = LambdaToSqlConverter.convert(chain, schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getSql()).contains("FROM app_article");
        assertThat(stmt.getSql()).contains("LEFT JOIN app_article_category");
        assertThat(stmt.getTables()).containsExactly("app_article", "app_article_category");
        assertThat(stmt.isSelectStar()).isFalse();
    }

    @Test
    void shouldCheckIndexPerTableInJoin() {
        // app_article.title 没有索引，但 app_article_category.category_id 有索引
        String[] lines = {
            "new MPJLambdaWrapper<AppArticleEntity>()",
            "    .leftJoin(AppArticleCategoryEntity.class, AppArticleCategoryEntity::getArticleId, AppArticleEntity::getId)",
            "    .eq(AppArticleEntity::getTitle, \"test\")",
            "    .eq(AppArticleCategoryEntity::getCategoryId, 5)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();

        // SQL-001: title 在 app_article 无索引 → 报告
        List<Issue> issues = new NoIndexWhereRule().check(stmt, schema);
        assertThat(issues).isNotEmpty();
        // 至少有一条关于 title 的报告
        assertThat(issues.stream().anyMatch(i -> i.getMessage().contains("title"))).isTrue();
    }

    @Test
    void shouldSupportInnerJoin() {
        String[] lines = {
            "new MPJLambdaWrapper<AppArticleEntity>()",
            "    .innerJoin(AppArticleCategoryEntity.class, AppArticleCategoryEntity::getArticleId, AppArticleEntity::getId)",
            "    .eq(AppArticleEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getSql()).contains("INNER JOIN app_article_category");
    }

    // ===== 边界情况 =====

    @Test
    void shouldReturnNullForNullChain() {
        SqlStatement stmt = LambdaToSqlConverter.convert(null, schema);
        assertThat(stmt).isNull();
    }

    @Test
    void shouldHandleNullSchema() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), null);
        assertThat(stmt).isNotNull();
        // 没有 schema 时仍然能生成 SQL（使用 NameConverter 回退）
        assertThat(stmt.getSql()).contains("FROM app_user");
    }

    @Test
    void shouldHandleApplySqlFragment() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"status = {0}\", 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getSql()).contains("status = ?");
    }

    // ===== 全规则综合测试 =====

    @Test
    void shouldRunAllRulesOnConvertedSql() {
        // 一个典型的问题查询：大表 + 无索引条件 + SELECT * + 无 LIMIT
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        SqlStatement stmt = LambdaToSqlConverter.convert(chains.get(0), schema);

        // 运行所有 SQL 规则
        List<SqlXmlRule> rules = List.of(
            new SelectStarXmlRule(),
            new NoIndexWhereRule(),
            new NoLimitLargeTableRule(),
            new LikeLeadingWildcardRule()
        );

        int totalIssues = 0;
        for (SqlXmlRule rule : rules) {
            List<Issue> issues = rule.check(stmt, schema);
            totalIssues += issues.size();
        }

        // 预期：SQL-003 (SELECT *) + SQL-001 (create_time 无索引) + SQL-101 (大表无 LIMIT)
        assertThat(totalIssues).isGreaterThanOrEqualTo(3);
    }
}
