package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaNoIndexRule 单元测试
 *
 * @author marker
 */
class LambdaNoIndexRuleTest {

    private LambdaNoIndexRule rule;
    private LambdaChainParser parser;
    private SchemaRegistry schema;

    @BeforeEach
    void setUp() {
        rule = new LambdaNoIndexRule();
        parser = new LambdaChainParser();
        schema = TestSchemaHelper.loadTestSchema();
    }

    @Test
    void shouldRaiseIssue_whenColumnHasNoIndex() {
        // app_user 表的 create_time 不是任何索引的最左列（在复合索引中是第二列）
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-303");
        assertThat(issues.get(0).getMessage()).contains("create_time");
    }

    @Test
    void shouldNotRaiseIssue_whenColumnHasIndex() {
        // app_user 表的 user_name 有 idx_user_name 索引
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserName, \"test\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenPrimaryKeyColumn() {
        // app_user 表的 user_id 是主键
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserId, 1L)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRaiseIssueForMultipleUnindexedColumns() {
        // app_article 表只有 id 有索引，title 和 category_id 都没有
        String[] lines = {
            "Wrappers.<AppArticleEntity>lambdaQuery()",
            "    .eq(AppArticleEntity::getTitle, \"test\")",
            "    .eq(AppArticleEntity::getCategoryId, 1L)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(2);
    }

    @Test
    void shouldSkipIsNullCondition() {
        // isNull 不需要索引检查
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .isNull(AppUserEntity::getEmail)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenTableNotInSchema() {
        // UnknownEntity 不在 schema 中，跳过检查
        String[] lines = {
            "Wrappers.<UnknownEntity>lambdaQuery()",
            "    .eq(UnknownEntity::getSomeField, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenSchemaIsNull() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), null);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenCompositeIndexCovers() {
        // app_user idx_status_create_time(status, create_time)，条件包含 status（最左列）→ 可用
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .eq(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenOnlyNonLeftmostColumn() {
        // create_time 不是任何索引的最左列
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getCreateTime, \"2024-01-01\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }
}
