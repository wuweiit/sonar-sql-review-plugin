package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoIndexWhereRule 单元测试（含降级场景）
 *
 * @author marker
 */
class NoIndexWhereRuleTest {

    private SchemaRegistry schema;
    private NoIndexWhereRule rule;

    @BeforeEach
    void setUp() {
        schema = TestSchemaHelper.loadTestSchema();
        rule = new NoIndexWhereRule();
    }

    @Test
    void shouldRaiseIssue_whenWhereColumnHasNoIndex() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("status"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-001");
        assertThat(issues.get(0).getMessage()).contains("status");
    }

    @Test
    void shouldNotRaiseIssue_whenWhereColumnHasIndex() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("user_name"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldCheckFallbackSchema_whenTableNotInPrimary() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_new_feature"))
                .conditionColumns(List.of("status"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getMessage()).contains("dev");
    }

    @Test
    void shouldNotRaiseIssue_whenTableNotInAnySchema() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("completely_unknown"))
                .conditionColumns(List.of("col"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNoConditionColumns() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of())
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldRaiseMultipleIssues_whenMultipleColumnsNoIndex() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_article"))
                .conditionColumns(List.of("title", "category_id"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(2);
    }
}
