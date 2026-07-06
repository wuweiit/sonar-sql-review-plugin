package com.yourcompany.sqlreview.tool;

import com.yourcompany.sqlreview.parser.SqlStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlDumpWriter 单元测试
 *
 * @author marker
 */
class SqlDumpWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectXmlSql() {
        SqlDumpWriter writer = new SqlDumpWriter();

        SqlStatement stmt = new SqlStatement();
        stmt.setSql("SELECT * FROM user WHERE id = ?");
        stmt.setLineNumber(10);

        writer.addXmlSql("mapper/UserMapper.xml", stmt);

        assertThat(writer.size()).isEqualTo(1);
        assertThat(writer.getEntries().get(0).getSource()).isEqualTo("XML");
        assertThat(writer.getEntries().get(0).getFilePath()).isEqualTo("mapper/UserMapper.xml");
        assertThat(writer.getEntries().get(0).getSql()).isEqualTo("SELECT * FROM user WHERE id = ?");
    }

    @Test
    void shouldCollectLambdaSql() {
        SqlDumpWriter writer = new SqlDumpWriter();

        writer.addLambdaSql("src/main/java/UserService.java", 25, "SELECT * FROM app_user WHERE status = ?");

        assertThat(writer.size()).isEqualTo(1);
        assertThat(writer.getEntries().get(0).getSource()).isEqualTo("LAMBDA");
        assertThat(writer.getEntries().get(0).getLineNumber()).isEqualTo(25);
    }

    @Test
    void shouldCollectMultipleSources() {
        SqlDumpWriter writer = new SqlDumpWriter();

        SqlStatement stmt = new SqlStatement();
        stmt.setSql("SELECT * FROM user");
        stmt.setLineNumber(5);

        writer.addXmlSql("UserMapper.xml", stmt);
        writer.addLambdaSql("UserService.java", 30, "SELECT * FROM app_user WHERE status = ?");
        writer.addSql("MANUAL", "test.sql", 1, "DELETE FROM temp");

        assertThat(writer.size()).isEqualTo(3);
    }

    @Test
    void shouldDumpToFile() throws IOException {
        SqlDumpWriter writer = new SqlDumpWriter();

        writer.addLambdaSql("User.java", 10, "SELECT * FROM app_user WHERE status = ?");
        writer.addLambdaSql("Order.java", 20, "SELECT * FROM order_info WHERE user_id = ?");

        Path outFile = tempDir.resolve("sql-dump.txt");
        writer.dumpToFile(outFile.toString());

        assertThat(outFile).exists();
        List<String> lines = Files.readAllLines(outFile, StandardCharsets.UTF_8);

        assertThat(lines.get(0)).contains("SQL Review");
        assertThat(lines).anyMatch(l -> l.contains("[LAMBDA]") && l.contains("User.java:10"));
        assertThat(lines).anyMatch(l -> l.contains("[LAMBDA]") && l.contains("Order.java:20"));
        assertThat(lines).anyMatch(l -> l.contains("Total: 2"));
    }

    @Test
    void shouldCreateParentDirectories() throws IOException {
        SqlDumpWriter writer = new SqlDumpWriter();
        writer.addLambdaSql("User.java", 1, "SELECT 1");

        Path outFile = tempDir.resolve("sub/dir/dump.txt");
        writer.dumpToFile(outFile.toString());

        assertThat(outFile).exists();
    }

    @Test
    void shouldSkipWhenPathIsNull() {
        SqlDumpWriter writer = new SqlDumpWriter();
        writer.addLambdaSql("User.java", 1, "SELECT 1");

        // 不应抛异常
        writer.dumpToFile(null);
        assertThat(writer.size()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenPathIsEmpty() {
        SqlDumpWriter writer = new SqlDumpWriter();
        writer.addLambdaSql("User.java", 1, "SELECT 1");

        writer.dumpToFile("");
        assertThat(writer.size()).isEqualTo(1);
    }

    @Test
    void shouldOverwriteExistingFile() throws IOException {
        SqlDumpWriter writer = new SqlDumpWriter();
        writer.addLambdaSql("User.java", 1, "SELECT 1");

        Path outFile = tempDir.resolve("dump.txt");
        Files.writeString(outFile, "old content");

        writer.dumpToFile(outFile.toString());

        List<String> lines = Files.readAllLines(outFile, StandardCharsets.UTF_8);
        assertThat(lines.get(0)).contains("SQL Review");
        assertThat(lines).noneMatch(l -> l.contains("old content"));
    }

    @Test
    void shouldFormatSqlEntryToString() {
        SqlDumpWriter.SqlEntry entry = new SqlDumpWriter.SqlEntry(
                "LAMBDA", "src/User.java", 42, "SELECT * FROM app_user");

        assertThat(entry.toString())
                .isEqualTo("[LAMBDA] src/User.java:42 | SELECT * FROM app_user");
    }

    @Test
    void shouldWriteEmptyFileWhenNoEntries() throws IOException {
        SqlDumpWriter writer = new SqlDumpWriter();

        Path outFile = tempDir.resolve("empty-dump.txt");
        writer.dumpToFile(outFile.toString());

        assertThat(outFile).exists();
        List<String> lines = Files.readAllLines(outFile, StandardCharsets.UTF_8);
        assertThat(lines).anyMatch(l -> l.contains("Total: 0"));
    }
}
