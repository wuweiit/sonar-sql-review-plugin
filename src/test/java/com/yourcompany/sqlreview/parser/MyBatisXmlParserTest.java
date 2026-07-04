package com.yourcompany.sqlreview.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatisXmlParser 单元测试
 *
 * @author marker
 */
class MyBatisXmlParserTest {

    private String loadTestXml(String filename) {
        try {
            return Files.readString(Path.of("src/test/resources/mappers/" + filename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test XML: " + filename, e);
        }
    }

    @Test
    void parse_goodMapper_returnsCorrectStatements() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("GoodMapper.xml"), "GoodMapper.xml");
        assertThat(stmts).hasSize(3);

        SqlStatement first = stmts.get(0);
        assertThat(first.getId()).isEqualTo("getUserById");
        assertThat(first.getType()).isEqualTo("SELECT");
        assertThat(first.getTables()).containsExactly("app_user");
        assertThat(first.isSelectStar()).isFalse();
    }

    @Test
    void parse_goodMapper_whereColumnsDetected() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("GoodMapper.xml"), "GoodMapper.xml");

        SqlStatement getUserById = stmts.stream()
                .filter(s -> "getUserById".equals(s.getId())).findFirst().orElseThrow();
        assertThat(getUserById.getConditionColumns()).contains("user_id");

        SqlStatement getUserByName = stmts.stream()
                .filter(s -> "getUserByName".equals(s.getId())).findFirst().orElseThrow();
        assertThat(getUserByName.getConditionColumns()).contains("user_name");
    }

    @Test
    void parse_badMapper_selectStarDetected() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("BadMapper.xml"), "BadMapper.xml");

        SqlStatement selectStar = stmts.stream()
                .filter(s -> "listAll".equals(s.getId())).findFirst().orElseThrow();
        assertThat(selectStar.isSelectStar()).isTrue();
    }

    @Test
    void parse_badMapper_dynamicConcatDetected() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("BadMapper.xml"), "BadMapper.xml");

        SqlStatement concat = stmts.stream()
                .filter(s -> "unsafeQuery".equals(s.getId())).findFirst().orElseThrow();
        assertThat(concat.hasDynamicConcat()).isTrue();
    }

    @Test
    void parse_badMapper_dynamicIfGeneratesVariants() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("BadMapper.xml"), "BadMapper.xml");

        SqlStatement dynamic = stmts.stream()
                .filter(s -> "searchUsers".equals(s.getId())).findFirst().orElseThrow();
        // 动态标签会产生至少一个变体（基础 SQL）
        assertThat(dynamic.getVariants()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void parse_badMapper_whereColumnsDetected() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("BadMapper.xml"), "BadMapper.xml");

        SqlStatement listByStatus = stmts.stream()
                .filter(s -> "listByStatus".equals(s.getId())).findFirst().orElseThrow();
        assertThat(listByStatus.getConditionColumns()).contains("status");
    }

    @Test
    void parse_goodMapper_noSelectStar() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("GoodMapper.xml"), "GoodMapper.xml");
        assertThat(stmts).noneMatch(SqlStatement::isSelectStar);
    }

    @Test
    void parse_goodMapper_noDynamicConcat() {
        List<SqlStatement> stmts = MyBatisXmlParser.parseContent(loadTestXml("GoodMapper.xml"), "GoodMapper.xml");
        assertThat(stmts).noneMatch(SqlStatement::hasDynamicConcat);
    }

    @Test
    void extractTables_basicSql() {
        List<String> tables = MyBatisXmlParser.extractTables("SELECT user_id FROM app_user WHERE user_id = ?");
        assertThat(tables).containsExactly("app_user");
    }

    @Test
    void extractTables_joinSql() {
        List<String> tables = MyBatisXmlParser.extractTables(
                "SELECT a.id FROM app_user a JOIN app_article b ON a.user_id = b.id");
        assertThat(tables).containsExactlyInAnyOrder("app_user", "app_article");
    }

    @Test
    void extractConditionColumns_basicWhere() {
        List<String> cols = MyBatisXmlParser.extractConditionColumns(
                "SELECT * FROM t WHERE user_name = ? AND status = ?");
        assertThat(cols).containsExactlyInAnyOrder("user_name", "status");
    }

    @Test
    void extractConditionColumns_noWhere() {
        List<String> cols = MyBatisXmlParser.extractConditionColumns("SELECT * FROM t");
        assertThat(cols).isEmpty();
    }
}
