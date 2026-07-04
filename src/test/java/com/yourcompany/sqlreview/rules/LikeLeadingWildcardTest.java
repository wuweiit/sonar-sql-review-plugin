package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LikeLeadingWildcardTest {
    private LikeLeadingWildcardRule rule;

    @BeforeEach
    void setUp() { rule = new LikeLeadingWildcardRule(); }

    @Test
    void shouldRaiseIssue_whenLikeWithLeadingWildcard() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM t WHERE col LIKE '%test'")
                .tables(List.of("t")).build();
        assertThat(rule.check(stmt, null)).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenLikeWithoutLeadingWildcard() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM t WHERE col LIKE 'test%'")
                .tables(List.of("t")).build();
        assertThat(rule.check(stmt, null)).isEmpty();
    }
}
