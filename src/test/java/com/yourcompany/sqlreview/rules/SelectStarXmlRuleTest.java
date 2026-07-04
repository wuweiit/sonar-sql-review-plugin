package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SelectStarXmlRule 单元测试
 *
 * @author marker
 */
class SelectStarXmlRuleTest {

    private SelectStarXmlRule rule;

    @BeforeEach
    void setUp() {
        rule = new SelectStarXmlRule();
    }

    @Test
    void shouldRaiseIssue_whenSelectStar() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT").selectStar(true)
                .tables(List.of("app_user")).build();

        List<Issue> issues = rule.check(stmt, null);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-003");
    }

    @Test
    void shouldNotRaiseIssue_whenExplicitColumns() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT").selectStar(false)
                .tables(List.of("app_user")).build();

        assertThat(rule.check(stmt, null)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNotSelect() {
        SqlStatement stmt = SqlStatement.builder()
                .type("INSERT").selectStar(true).build();

        assertThat(rule.check(stmt, null)).isEmpty();
    }
}
