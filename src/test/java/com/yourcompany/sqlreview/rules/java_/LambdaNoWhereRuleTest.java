package com.yourcompany.sqlreview.rules.java_;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaNoWhereRule 单元测试
 *
 * @author marker
 */
class LambdaNoWhereRuleTest {

    private LambdaNoWhereRule rule;

    @BeforeEach
    void setUp() {
        rule = new LambdaNoWhereRule();
    }

    @Test
    void shouldRaiseIssue_whenNoWhereCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .select(AppUserEntity::getUserId, AppUserEntity::getUserName)",
            "    .list();"
        };

        List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-302");
        assertThat(issues.get(0).getLine()).isEqualTo(1);
    }

    @Test
    void shouldNotRaiseIssue_whenHasEqCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .select(AppUserEntity::getUserId, AppUserEntity::getUserName)",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenHasLikeCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .like(AppUserEntity::getUserName, \"test\")",
            "    .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenHasInCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .in(AppUserEntity::getStatus, List.of(1, 2, 3))",
            "    .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenHasLastCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .last(\"LIMIT 10\");"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenLambdaQueryWrapperWithCondition() {
        String[] lines = {
            "new LambdaQueryWrapper<AppUserEntity>()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .orderByDesc(AppUserEntity::getCreateTime);"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenLambdaQueryWrapperNoCondition() {
        String[] lines = {
            "new LambdaQueryWrapper<AppUserEntity>()",
            "    .orderByDesc(AppUserEntity::getCreateTime)",
            "    .list();"
        };

        List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-302");
    }

    @Test
    void shouldSkipCommentedLines() {
        String[] lines = {
            "// Wrappers.<AppUserEntity>lambdaQuery()",
            "//     .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenHasBetweenCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .between(AppUserEntity::getCreateTime, start, end)",
            "    .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }
}
