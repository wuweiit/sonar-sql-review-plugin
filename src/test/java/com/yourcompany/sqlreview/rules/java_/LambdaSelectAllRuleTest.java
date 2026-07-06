package com.yourcompany.sqlreview.rules.java_;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaSelectAllRule 单元测试
 *
 * @author marker
 */
class LambdaSelectAllRuleTest {

    private LambdaSelectAllRule rule;

    @BeforeEach
    void setUp() {
        rule = new LambdaSelectAllRule();
    }

    @Test
    void shouldRaiseIssue_whenSelectAll() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .selectAll(AppUserEntity.class)",
            "    .eq(AppUserEntity::getStatus, 1);"
        };

        List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getRuleId()).isEqualTo("SQL-301");
        assertThat(issues.get(0).getLine()).isEqualTo(2);
    }

    @Test
    void shouldNotRaiseIssue_whenExplicitSelect() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .select(AppUserEntity::getUserId, AppUserEntity::getUserName)",
            "    .eq(AppUserEntity::getStatus, 1);"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldNotRaiseIssue_whenCommented() {
        String[] lines = {
            "// .selectAll(AppUserEntity.class)",
            "/* .selectAll(AppUserEntity.class) */",
            " * .selectAll(AppUserEntity.class)"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldRaiseIssueOnCorrectLine() {
        String[] lines = {
            "public void example() {",
            "    var wrapper = Wrappers.<AppUserEntity>lambdaQuery()",
            "        .selectAll(AppUserEntity.class);",
            "}"
        };

        List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getLine()).isEqualTo(3);
    }

    @Test
    void shouldDetectMultipleSelectAll() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery().selectAll(AppUserEntity.class);",
            "Wrappers.<OrderEntity>lambdaQuery().selectAll(OrderEntity.class);"
        };

        List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
        assertThat(issues).hasSize(2);
    }

    @Test
    void shouldNotRaiseIssue_whenNoSelectAll() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .select(AppUserEntity::getUserId)",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        assertThat(rule.check(lines)).isEmpty();
    }

    @Test
    void shouldDetectSelectAllFromBadTestResource() throws IOException {
        Path path = Path.of("src/test/resources/java/BadLambdaUsage.java");
        if (Files.exists(path)) {
            String content = Files.readString(path);
            String[] lines = content.split("\n", -1);
            List<LambdaSelectAllRule.JavaIssue> issues = rule.check(lines);
            assertThat(issues).isNotEmpty();
            assertThat(issues).allMatch(i -> "SQL-301".equals(i.getRuleId()));
        }
    }
}
