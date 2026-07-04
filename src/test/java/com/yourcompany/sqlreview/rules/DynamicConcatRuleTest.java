package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicConcatRuleTest {
    private DynamicConcatRule rule;

    @BeforeEach
    void setUp() { rule = new DynamicConcatRule(); }

    @Test
    void shouldRaiseIssue_whenHasDynamicConcat() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .hasDynamicConcat(true).tables(List.of("t")).build();
        List<Issue> issues = rule.check(stmt, null);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-201");
    }

    @Test
    void shouldNotRaiseIssue_whenNoDynamicConcat() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .hasDynamicConcat(false).tables(List.of("t")).build();
        assertThat(rule.check(stmt, null)).isEmpty();
    }
}
