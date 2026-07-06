package com.yourcompany.sqlreview.rules.java_;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LambdaChainParser 单元测试
 *
 * @author marker
 */
class LambdaChainParserTest {

    private LambdaChainParser parser;

    @BeforeEach
    void setUp() {
        parser = new LambdaChainParser();
    }

    @Test
    void shouldParseEntityClass() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).getEntityClass()).isEqualTo("AppUserEntity");
    }

    @Test
    void shouldParseConditionColumns() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserId, 1L)",
            "    .like(AppUserEntity::getUserName, \"test\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);

        LambdaChain chain = chains.get(0);
        assertThat(chain.getConditions()).hasSize(2);
        assertThat(chain.getConditions().get(0).getMethod()).isEqualTo("eq");
        assertThat(chain.getConditions().get(0).getColumnName()).isEqualTo("user_id");
        assertThat(chain.getConditions().get(0).getGetterName()).isEqualTo("getUserId");
        assertThat(chain.getConditions().get(1).getMethod()).isEqualTo("like");
        assertThat(chain.getConditions().get(1).getColumnName()).isEqualTo("user_name");
    }

    @Test
    void shouldDetectHasLast() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .last(\"LIMIT 10\")",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains.get(0).isHasLast()).isTrue();
    }

    @Test
    void shouldDetectHasTermination() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains.get(0).isHasTermination()).isTrue();
    }

    @Test
    void shouldDetectSelectAll() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .selectAll(AppUserEntity.class)",
            "    .eq(AppUserEntity::getStatus, 1);",
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains.get(0).isHasSelectAll()).isTrue();
    }

    @Test
    void shouldParseLambdaQueryWrapper() {
        String[] lines = {
            "new LambdaQueryWrapper<AppUserEntity>()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).getEntityClass()).isEqualTo("AppUserEntity");
    }

    @Test
    void shouldParseBooleanPrefixCondition() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(StrUtil.isNotBlank(name), AppUserEntity::getUserName, name)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(1);
        assertThat(chains.get(0).getConditions()).hasSize(1);
        assertThat(chains.get(0).getConditions().get(0).getColumnName()).isEqualTo("user_name");
    }

    @Test
    void shouldSkipComments() {
        String[] lines = {
            "// Wrappers.<AppUserEntity>lambdaQuery()",
            "//     .eq(AppUserEntity::getStatus, 1)",
            "//     .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).isEmpty();
    }

    @Test
    void shouldParseMultipleChains() {
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();",
            "",
            "Wrappers.<OrderEntity>lambdaQuery()",
            "    .eq(OrderEntity::getUserId, 1L)",
            "    .list();"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains).hasSize(2);
    }

    @Test
    void shouldRecordStartLine() {
        String[] lines = {
            "public void example() {",
            "    Wrappers.<AppUserEntity>lambdaQuery()",
            "        .eq(AppUserEntity::getStatus, 1)",
            "        .list();",
            "}"
        };

        List<LambdaChain> chains = parser.parse(lines);
        assertThat(chains.get(0).getStartLine()).isEqualTo(2);
    }
}
