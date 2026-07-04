package com.yourcompany.sqlreview.rules;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.TestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FullTableScanRule 单元测试（含降级场景）
 *
 * @author marker
 */
class FullTableScanRuleTest {

    private SchemaRegistry schema;
    private FullTableScanRule rule;

    @BeforeEach
    void setUp() {
        schema = TestSchemaHelper.loadTestSchema();
        rule = new FullTableScanRule();
    }

    @Test
    void shouldRaiseIssue_whenLargeTableNoWhere() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of())
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-002");
    }

    @Test
    void shouldNotRaiseIssue_whenFallbackTableIsSmall() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_new_feature"))
                .conditionColumns(List.of())
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenAllConditionColumnsHaveNoIndex() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("status"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
    }

    @Test
    void shouldNotRaiseIssue_whenConditionColumnHasIndex() {
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("user_name"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenNotSelect() {
        SqlStatement stmt = SqlStatement.builder()
                .type("UPDATE")
                .tables(List.of("app_user"))
                .conditionColumns(List.of())
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }
}
