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
        // app_user 150k行，create_time 不是任何索引的最左列
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("create_time"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-001");
        assertThat(issues.get(0).getMessage()).contains("create_time");
    }

    @Test
    void shouldNotRaiseIssue_whenWhereColumnHasIndex() {
        // user_name 是 idx_user_name 的最左列
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("user_name"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenSmallTable() {
        // app_new_feature 只有 100 行，小表不需要索引
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_new_feature"))
                .conditionColumns(List.of("status"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
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
    void shouldRaiseOneIssue_whenMultipleColumnsNoIndex() {
        // app_article 200k行，title和category_id都无索引 → 只报一条
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_article"))
                .conditionColumns(List.of("title", "category_id"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getMessage()).contains("title").contains("category_id");
    }

    @Test
    void shouldNotRaiseIssue_whenCompositeIndexCovers() {
        // app_user idx_status_create_time(status, create_time) + 条件包含 status（最左列）→ 可用
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("status", "create_time"))
                .build();

        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void shouldRaiseIssue_whenOnlyNonLeftmostColumn() {
        // create_time 不是最左列，即使和 status 在同一个复合索引中
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("create_time"))
                .build();

        List<Issue> issues = rule.check(stmt, schema);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getMessage()).contains("无法命中任何索引");
    }

    @Test
    void check_withDatabase_usesSpecifiedDbMetadata() {
        // shared_db.app_user 的 name 列有 idx_name 索引
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("name"))
                .database("shared_db")
                .build();

        // shared_db 中 name 有索引，不报告
        assertThat(rule.check(stmt, schema)).isEmpty();
    }

    @Test
    void check_withNullDatabase_stillWorks() {
        // null database 时使用兼容模式
        SqlStatement stmt = SqlStatement.builder()
                .type("SELECT")
                .tables(List.of("app_user"))
                .conditionColumns(List.of("create_time"))
                .database(null)
                .build();

        assertThat(rule.check(stmt, schema)).hasSize(1);
    }
}
