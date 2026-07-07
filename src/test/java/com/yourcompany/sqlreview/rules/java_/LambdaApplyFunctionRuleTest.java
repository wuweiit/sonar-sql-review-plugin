package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaApplyFunctionRule 单元测试
 *
 * @author marker
 */
class LambdaApplyFunctionRuleTest {

    private LambdaApplyFunctionRule rule;
    private LambdaChainParser parser;
    private SchemaRegistry schema;

    @BeforeEach
    void setUp() {
        rule = new LambdaApplyFunctionRule();
        parser = new LambdaChainParser();
        schema = TestSchemaHelper.loadTestSchema();
    }

    @Test
    void shouldRaiseIssue_whenYearFunctionOnIndexedColumn() {
        // app_user 表的 user_id 有 PRIMARY 索引
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"YEAR(user_id) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).getApplyCalls()).hasSize(1);

        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-306");
        assertThat(issues.get(0).getMessage()).contains("user_id");
    }

    @Test
    void shouldRaiseIssue_whenDateFunctionOnIndexedColumn() {
        // app_user 表的 email 有 uk_email 索引
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"UPPER(email) = {0}\", \"TEST@EXAMPLE.COM\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getMessage()).contains("email");
    }

    @Test
    void shouldNotRaiseIssue_whenNoFunctionOnColumn() {
        // apply 中没有函数调用
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"status = {0}\", 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenFunctionOnNonIndexedColumn() {
        // app_user 表的 create_time 不是任何索引的最左列，使用函数不影响（反正也没索引）
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"YEAR(create_time) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        // create_time 没有索引，所以不报告 SQL-306
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNoApplyCalls() {
        // 链中没有 .apply() 调用
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
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
            "    .apply(\"YEAR(user_id) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), null);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenTableNotInSchema() {
        String[] lines = {
            "Wrappers.<UnknownEntity>lambdaQuery()",
            "    .apply(\"YEAR(some_col) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectMultipleFunctionsInOneApply() {
        // 一个 apply 中有多个函数
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"YEAR(user_id) = {0} AND MONTH(user_name) = {1}\", 2024, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        // user_id 有索引，user_name 也有索引，都会报告
        assertThat(issues).hasSize(2);
    }

    @Test
    void shouldHandleLowerFunction() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"LOWER(user_name) = {0}\", \"john\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }

    @Test
    void check_withDatabase_usesSpecifiedDbMetadata() {
        // shared_db.app_user 的 user_id 列有 PRIMARY 索引
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"YEAR(id) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase("shared_db");
        // shared_db.app_user 的 id 有 PRIMARY 索引，函数操作会报告
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }

    @Test
    void check_withNullDatabase_stillWorks() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .apply(\"YEAR(user_id) = {0}\", 2024)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(null);
        List<JavaIssue> issues = rule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }
}
