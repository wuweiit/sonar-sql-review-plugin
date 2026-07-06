package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexFunctionRuleTest {
    private SchemaRegistry schema;
    private IndexFunctionRule rule;

    @BeforeEach
    void setUp() {
        schema = TestSchemaHelper.loadTestSchema();
        rule = new IndexFunctionRule();
    }

    @Test
    void shouldRaiseIssue_whenFunctionOnIndexedColumn() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM app_user WHERE LOWER(user_name) = 'test'")
                .tables(List.of("app_user")).build();
        assertThat(rule.check(stmt, schema)).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenFunctionOnNonIndexedColumn() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM app_user WHERE YEAR(create_time) = 2023")
                .tables(List.of("app_user")).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNoFunctionInWhere() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM app_user WHERE user_name = 'test'")
                .tables(List.of("app_user")).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNoWhereClause() {
        SqlStatement stmt = SqlStatement.builder().type("SELECT")
                .sql("SELECT * FROM app_user")
                .tables(List.of("app_user")).build();
        assertThat(rule.check(stmt, schema)).isEmpty();
    }
}
