package com.yourcompany.sqlreview.tool;

import com.yourcompany.sqlreview.parser.MyBatisXmlParser;
import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.rules.*;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MyBatis XML SQL Review - 独立报告生成器
 * 在 SonarQube 扫描前运行，生成 External Issue Import 报告
 *
 * 用法:
 *   java -cp sonar-sql-review-plugin-1.0.0.jar com.yourcompany.sqlreview.tool.ReportGenerator \
 *     --project-dir /path/to/project \
 *     --schema-dir /path/to/schemas \
 *     --output .scannerwork/sql-review-report.xml
 *
 * @author marker
 */
public class ReportGenerator {

    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println("Usage: java -cp jar com.yourcompany.sqlreview.tool.ReportGenerator \\");
            System.err.println("  --project-dir <dir> --schema-dir <dir> --output <file> [--env <env>]");
            System.exit(1);
        }

        String projectDir = null;
        String schemaDir = null;
        String outputFile = null;
        String env = "production";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--project-dir":
                    projectDir = args[++i];
                    break;
                case "--schema-dir":
                    schemaDir = args[++i];
                    break;
                case "--output":
                    outputFile = args[++i];
                    break;
                case "--env":
                    env = args[++i];
                    break;
            }
        }

        if (projectDir == null || schemaDir == null || outputFile == null) {
            System.err.println("Error: --project-dir, --schema-dir, and --output are required");
            System.exit(1);
        }

        try {
            int issueCount = generateReport(projectDir, schemaDir, outputFile, env);
            System.out.println("Done: " + issueCount + " issues found");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int generateReport(String projectDir, String schemaDir, String outputFile, String env) throws Exception {
        // 1. 加载 Schema
        SchemaRegistry schema = new SchemaRegistry();
        schema.loadFromPaths(schemaDir, env, "dev");

        System.err.println("Loaded " + schema.getPrimaryTableCount() + " tables from " + env);

        // 2. 查找所有 XML 文件
        Path projectPath = Paths.get(projectDir);
        List<Path> xmlFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectPath)) {
            walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".xml"))
                .filter(p -> {
                    String pathStr = p.toString().toLowerCase();
                    return pathStr.contains("mapper") || pathStr.contains("mybatis");
                })
                .forEach(xmlFiles::add);
        }

        System.err.println("Found " + xmlFiles.size() + " XML files");

        // 3. 分析并生成报告
        List<SqlXmlRule> rules = new ArrayList<>();
        rules.add(new SelectStarXmlRule());
        rules.add(new NoIndexWhereRule());
        rules.add(new FullTableScanRule());
        rules.add(new LikeLeadingWildcardRule());
        rules.add(new NoLimitLargeTableRule());
        rules.add(new DynamicConcatRule());
        rules.add(new IndexFunctionRule());

        List<IssueRecord> allIssues = new ArrayList<>();

        for (Path xmlFile : xmlFiles) {
            List<SqlStatement> stmts = MyBatisXmlParser.parseFile(xmlFile);
            if (stmts.isEmpty()) {
                continue;
            }

            System.err.println("Parsed " + stmts.size() + " SQL statements from " + xmlFile.getFileName());

            for (SqlStatement stmt : stmts) {
                for (SqlXmlRule rule : rules) {
                    List<Issue> issues = rule.check(stmt, schema);
                    for (Issue issue : issues) {
                        allIssues.add(new IssueRecord(xmlFile, stmt, issue));
                    }
                }
            }
        }

        // 4. 写入报告
        if (!allIssues.isEmpty()) {
            writeReport(outputFile, allIssues, projectPath);
        }

        System.err.println("Generated " + allIssues.size() + " issues in " + outputFile);
        return allIssues.size();
    }

    private static void writeReport(String outputFile, List<IssueRecord> issues, Path projectPath) throws Exception {
        File outFile = new File(outputFile);
        outFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("issues");

            for (IssueRecord record : issues) {
                writer.writeStartElement("issue");

                writer.writeStartElement("engineId");
                writer.writeCharacters(SqlRulesDefinition.REPO_KEY);
                writer.writeEndElement();

                writer.writeStartElement("ruleId");
                writer.writeCharacters(record.issue.getRuleId());
                writer.writeEndElement();

                writer.writeStartElement("severity");
                writer.writeCharacters(record.issue.getSeverity());
                writer.writeEndElement();

                writer.writeStartElement("primaryLocation");

                writer.writeStartElement("message");
                writer.writeCharacters(record.issue.getMessage());
                writer.writeEndElement();

                writer.writeStartElement("filePath");
                writer.writeCharacters(projectPath.relativize(record.xmlFile).toString());
                writer.writeEndElement();

                writer.writeStartElement("textRange");
                writer.writeStartElement("startLine");
                writer.writeCharacters(String.valueOf(Math.max(1, record.stmt.getLineNumber())));
                writer.writeEndElement();
                writer.writeEndElement(); // textRange

                writer.writeEndElement(); // primaryLocation
                writer.writeEndElement(); // issue
            }

            writer.writeEndElement(); // issues
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        }
    }

    private static class IssueRecord {
        final Path xmlFile;
        final SqlStatement stmt;
        final Issue issue;

        IssueRecord(Path xmlFile, SqlStatement stmt, Issue issue) {
            this.xmlFile = xmlFile;
            this.stmt = stmt;
            this.issue = issue;
        }
    }
}
