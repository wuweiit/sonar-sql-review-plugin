package com.yourcompany.sqlreview.tool;

import com.yourcompany.sqlreview.parser.SqlStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 收集与输出工具
 * <p>
 * 在扫描过程中收集所有 SQL 语句（XML 原始 SQL + Lambda 转换 SQL），
 * 并可选输出到日志和指定文件。
 * </p>
 *
 * @author marker
 */
public class SqlDumpWriter {

    private static final Logger LOG = LoggerFactory.getLogger(SqlDumpWriter.class);

    private final List<SqlEntry> entries = new ArrayList<>();

    /**
     * 记录一条 SQL
     */
    public void addSql(String source, String filePath, int lineNumber, String sql) {
        entries.add(new SqlEntry(source, filePath, lineNumber, sql));
        LOG.info("[SQL Dump] [{}] {}:{}  {}", source, filePath, lineNumber, sql);
    }

    /**
     * 记录 XML 解析出的 SQL
     */
    public void addXmlSql(String filePath, SqlStatement stmt) {
        addSql("XML", filePath, stmt.getLineNumber(), stmt.getSql());
    }

    /**
     * 记录 Lambda 转换出的 SQL
     */
    public void addLambdaSql(String filePath, int lineNumber, String sql) {
        addSql("LAMBDA", filePath, lineNumber, sql);
    }

    /**
     * 将收集到的所有 SQL 写入指定文件
     *
     * @param outputPath 输出文件路径，为空或 null 时不写入
     */
    public void dumpToFile(String outputPath) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            LOG.info("SQL dump file not configured, skipping file output ({} entries collected)", entries.size());
            return;
        }

        Path path = Paths.get(outputPath.trim());
        try {
            // 确保父目录存在
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            List<String> lines = new ArrayList<>();
            lines.add("# SQL Review - Collected SQL Statements");
            lines.add("# Source: XML=MyBatis XML解析, LAMBDA=Lambda转换");
            lines.add("# Format: [SOURCE] filePath:lineNumber | SQL");
            lines.add("# Total: " + entries.size() + " statements");
            lines.add("");

            for (SqlEntry entry : entries) {
                lines.add(String.format("[%s] %s:%d | %s",
                        entry.source, entry.filePath, entry.lineNumber, entry.sql));
            }

            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            LOG.info("SQL dump written to {} ({} entries)", path, entries.size());
        } catch (IOException e) {
            LOG.error("Failed to write SQL dump file: {}", path, e);
        }
    }

    /**
     * 获取收集到的 SQL 条目数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 获取所有收集的条目
     */
    public List<SqlEntry> getEntries() {
        return entries;
    }

    /**
     * 单条 SQL 记录
     */
    public static class SqlEntry {
        private final String source;     // XML 或 LAMBDA
        private final String filePath;   // 源文件路径
        private final int lineNumber;    // 行号
        private final String sql;        // SQL 文本

        public SqlEntry(String source, String filePath, int lineNumber, String sql) {
            this.source = source;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.sql = sql;
        }

        public String getSource() { return source; }
        public String getFilePath() { return filePath; }
        public int getLineNumber() { return lineNumber; }
        public String getSql() { return sql; }

        @Override
        public String toString() {
            return String.format("[%s] %s:%d | %s", source, filePath, lineNumber, sql);
        }
    }
}
