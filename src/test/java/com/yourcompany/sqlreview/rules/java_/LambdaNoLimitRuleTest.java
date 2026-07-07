package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaNoLimitRule 单元测试
 *
 * @author marker
 */
class LambdaNoLimitRuleTest {

    private LambdaNoLimitRule rule;
    private LambdaChainParser parser;
    private SchemaRegistry schema;

    @BeforeEach
    void setUp() {
        rule = new LambdaNoLimitRule();
        parser = new LambdaChainParser();
        schema = TestSchemaHelper.loadTestSchema();
    }

    @Test
    void shouldRaiseIssue_whenLargeTableWithoutLimit() {
        // app_user 表有 150,000 行，超过默认阈值 10,000
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-305");
        assertThat(issues.get(0).getMessage()).contains("app_user");
    }

    @Test
    void shouldNotRaiseIssue_whenHasLast() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .last(\"LIMIT 100\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenHasPagination() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .page(page);"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenSmallTable() {
        // 使用高阈值规则，app_user 150,000 行小于阈值
        LambdaNoLimitRule highThresholdRule = new LambdaNoLimitRule(200000L);

        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = highThresholdRule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenLargeTableWithOnlyList() {
        // order_info 表有 500,000 行
        String[] lines = {
            "Wrappers.<OrderInfoEntity>lambdaQuery()",
            "    .eq(OrderInfoEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenTableNotInSchema() {
        String[] lines = {
            "Wrappers.<UnknownEntity>lambdaQuery()",
            "    .eq(UnknownEntity::getStatus, 1)",
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
    void shouldUseCustomThreshold() {
        LambdaNoLimitRule customRule = new LambdaNoLimitRule(1000000L);

        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = customRule.check(chains.get(0), schema);
        // app_user 150,000 < 1,000,000 阈值，不报告
        assertThat(issues).isEmpty();
    }

    @Test
    void check_withDatabase_usesSpecifiedDbMetadata() {
        // shared_db.app_user 只有 5000 行，不触发大表规则
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase("shared_db");
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        // shared_db.app_user 5000 行 < 10000 阈值，不报告
        assertThat(issues).isEmpty();
    }

    @Test
    void check_withNullDatabase_stillWorks() {
        // null database 时使用兼容模式（遍历所有库）
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(null);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        // app_production.app_user 150000 行，触发规则
        assertThat(issues).hasSize(1);
    }
}
