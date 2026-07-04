package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLimitLargeTableRuleTest {
    private SchemaRegistry schema;
    private NoLimitLargeTableRule rule;

    @BeforeEach
    void setUp() {
        schema = TestSchemaHelper.loadTestSchema();
        rule = new NoLimitLargeTableRule();
    }

    @Test
    void shouldRaiseIssue_whenLargeTableNoLimit() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .tables(List.of("app_user")).hasLimit(false).build();
        assertThat(rule.check(stmt, schema)).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenHasLimit() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .tables(List.of("app_user")).hasLimit(true).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenSmallTable() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .tables(List.of("app_new_feature")).hasLimit(false).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNotSelect() {
        SqlStatement stmt = SqlStatement.builder().type("DELETE")
                .tables(List.of("app_user")).hasLimit(false).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }
}
