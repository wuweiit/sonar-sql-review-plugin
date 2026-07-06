package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaLikeWildcardRule 单元测试
 *
 * @author marker
 */
class LambdaLikeWildcardRuleTest {

    private LambdaLikeWildcardRule rule;
    private LambdaChainParser parser;

    @BeforeEach
    void setUp() {
        rule = new LambdaLikeWildcardRule();
        parser = new LambdaChainParser();
    }

    @Test
    void shouldRaiseIssue_whenLikeWithLeadingPercent() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .like(AppUserEntity::getUserName, \"%张\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-304");
        assertThat(issues.get(0).getMessage()).contains("user_name");
    }

    @Test
    void shouldRaiseIssue_whenLikeWithPercentPrefix() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .like(AppUserEntity::getUserName, \"%\" + keyword)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenLikeRight() {
        // likeRight 是右模糊，可以使用索引
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .likeRight(AppUserEntity::getUserName, \"张\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenLikeWithoutLeadingPercent() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .like(AppUserEntity::getUserName, \"张%\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenEqCondition() {
        // eq 不是 LIKE 方法，不检查
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserName, \"%test%\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenLikeLeft() {
        // likeLeft 本身就是左模糊，应该报告
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .likeLeft(AppUserEntity::getUserName, \"张\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        // likeLeft 方法名本身就表示左模糊，应该报告
        assertThat(issues).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenNoConditions() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        List<JavaIssue> issues = rule.check(chains.get(0));
        assertThat(issues).isEmpty();
    }
}
