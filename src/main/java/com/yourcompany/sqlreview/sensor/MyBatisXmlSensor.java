package com.yourcompany.sqlreview.sensor;

import com.yourcompany.sqlreview.parser.MyBatisXmlParser;
import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.rules.*;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis XML SQL Review Sensor
 * 解析 Mapper XML 并通过 SonarQube API 上报 Issue
 *
 * @author marker
 */
public class MyBatisXmlSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisXmlSensor.class);
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private final List<SqlXmlRule> rules = new ArrayList<>();

    public MyBatisXmlSensor() {
        rules.add(new SelectStarXmlRule());
        rules.add(new NoIndexWhereRule());
        rules.add(new FullTableScanRule());
        rules.add(new LikeLeadingWildcardRule());
        rules.add(new NoLimitLargeTableRule());
        rules.add(new DynamicConcatRule());
        rules.add(new IndexFunctionRule());
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .onlyOnLanguage(SqlRulesDefinition.LANGUAGE)
                .name("MyBatis XML SQL Review");
    }

    @Override
    public void execute(SensorContext context) {
        LOG.info("Starting MyBatis XML SQL Review...");

        // 1. 加载 Schema
        SchemaRegistry schema = new SchemaRegistry();
        schema.load(context.config());
        LOG.info("Schema loaded: {} primary tables, {} fallback tables",
                schema.getPrimaryTableCount(), schema.getFallbackTableCount());

        // 2. 诊断：检查规则是否激活
        String[] ruleIds = {
            SqlRulesDefinition.SQL_001, SqlRulesDefinition.SQL_002, SqlRulesDefinition.SQL_003,
            SqlRulesDefinition.SQL_004, SqlRulesDefinition.SQL_101, SqlRulesDefinition.SQL_103,
            SqlRulesDefinition.SQL_201
        };
        for (String ruleId : ruleIds) {
            RuleKey key = RuleKey.of(SqlRulesDefinition.REPO_KEY, ruleId);
            ActiveRule activeRule = context.activeRules().find(key);
            if (activeRule == null) {
                LOG.warn("  Rule {} is NOT active in this project's quality profile!", key);
            } else {
                LOG.info("  Rule {} is ACTIVE (severity={})", key, activeRule.severity());
            }
        }

        // 3. 构建 Java InputFile 索引（按全限定类名）
        FileSystem fs = context.fileSystem();
        Map<String, InputFile> javaFileIndex = buildJavaFileIndex(fs);
        LOG.info("Java file index built: {} files", javaFileIndex.size());

        // 4. 遍历 XML 文件
        int fileCount = 0;
        int issueCount = 0;

        for (InputFile xmlFile : fs.inputFiles(
                fs.predicates().hasExtension("xml"))) {

            List<SqlStatement> stmts = MyBatisXmlParser.parse(xmlFile);
            if (stmts.isEmpty()) {
                continue;
            }

            fileCount++;
            LOG.info("Parsed {} SQL statements from {} (lang={}, type={}, status={})",
                    stmts.size(), xmlFile.filename(),
                    xmlFile.language(), xmlFile.type(), xmlFile.status());

            for (SqlStatement stmt : stmts) {
                // 通过 namespace 找到对应的 Java 文件
                InputFile javaFile = findJavaFile(javaFileIndex, stmt);
                
                // 选择 Issue 挂载的文件
                InputFile targetFile = selectTargetFile(javaFile, xmlFile);
                if (targetFile == null) {
                    LOG.debug("No target file for namespace: {} (xml: {})", stmt.getNamespace(), xmlFile.filename());
                    continue;
                }

                for (SqlXmlRule rule : rules) {
                    List<Issue> issues = rule.check(stmt, schema);
                    for (Issue issue : issues) {
                        if (!isRuleActive(context, issue.getRuleId())) {
                            LOG.debug("Skipping inactive SQL Review rule {}", issue.getRuleId());
                            continue;
                        }
                        if (reportIssue(context, targetFile, stmt, issue)) {
                            issueCount++;
                        }
                    }
                }
            }
        }

        LOG.info("MyBatis XML SQL Review complete: {} files analyzed, {} issues reported",
                fileCount, issueCount);
    }

    /**
     * 构建 Java InputFile 索引
     * key: 全限定类名 (如 com.example.mapper.UserMapper)
     * value: InputFile
     */
    private Map<String, InputFile> buildJavaFileIndex(FileSystem fs) {
        Map<String, InputFile> index = new HashMap<>();
        for (InputFile javaFile : fs.inputFiles(
                fs.predicates().hasLanguage("java"))) {
            // 从相对路径推导全限定类名
            // e.g. src/main/java/com/example/mapper/UserMapper.java -> com.example.mapper.UserMapper
            String fqcn = javaFileToClassName(javaFile);
            if (fqcn != null) {
                index.put(fqcn, javaFile);
            }
        }
        return index;
    }

    /**
     * Resolve a Java file to its fully qualified class name.
     */
    private String javaFileToClassName(InputFile javaFile) {
        String filename = javaFile.filename();
        if (!filename.endsWith(".java")) {
            return null;
        }

        try {
            Matcher matcher = PACKAGE_PATTERN.matcher(javaFile.contents());
            if (matcher.find()) {
                return matcher.group(1) + "." + filename.substring(0, filename.length() - 5);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read Java file for package detection: {}", javaFile.relativePath(), e);
        }

        String path = javaFile.relativePath().replace('\\', '/');
        return pathToClassName(path);
    }

    private String pathToClassName(String path) {
        // 查找 src/main/java/ 或 src/test/java/ 之后的部分
        int idx = path.indexOf("src/main/java/");
        if (idx < 0) idx = path.indexOf("src/test/java/");
        if (idx < 0) return null;

        String afterSrc = path.substring(idx);
        afterSrc = afterSrc.replaceFirst("src/(main|test)/java/", "");
        if (afterSrc.endsWith(".java")) {
            afterSrc = afterSrc.substring(0, afterSrc.length() - 5);
        }
        return afterSrc.replace('/', '.');
    }

    /**
     * 根据 SqlStatement 的 namespace 找到对应的 Java InputFile
     */
    private InputFile findJavaFile(Map<String, InputFile> javaFileIndex, SqlStatement stmt) {
        String namespace = stmt.getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            return null;
        }
        return javaFileIndex.get(namespace);
    }

    /**
     * 选择 Issue 挂载的目标文件
     * 规则语言是 java，必须挂载到 Java 文件上
     */
    private boolean isRuleActive(SensorContext context, String ruleId) {
        return context.activeRules().find(RuleKey.of(SqlRulesDefinition.REPO_KEY, ruleId)) != null;
    }

    private InputFile selectTargetFile(InputFile javaFile, InputFile xmlFile) {
        // 必须挂载到 Java 文件（规则语言是 java）
        if (javaFile != null) {
            return javaFile;
        }
        LOG.warn("Cannot report SQL Review issue for {}: no Java mapper matched namespace",
                xmlFile.relativePath());
        return null;
    }

    private boolean reportIssue(SensorContext context, InputFile targetFile, SqlStatement stmt, Issue issue) {
        RuleKey ruleKey = RuleKey.of(SqlRulesDefinition.REPO_KEY, issue.getRuleId());
        int issueLine = findJavaMethodLine(targetFile, stmt.getId());
        if (issueLine <= 0) {
            issueLine = 1;
            LOG.warn("Mapper method {} not found in {}; reporting SQL issue on line 1. Source XML: {}:{}",
                    stmt.getId(), targetFile.relativePath(), stmt.getFilePath(), stmt.getLineNumber());
        }

        try {
            NewIssue newIssue = context.newIssue();
            NewIssueLocation location = newIssue.newLocation()
                    .on(targetFile)
                    .at(targetFile.selectLine(issueLine))
                    .message("[SQL Review] " + issue.getMessage()
                            + " (from " + stmt.getFilePath() + ":" + stmt.getLineNumber() + ")");

            newIssue.forRule(ruleKey)
                    .at(location)
                    .save();
            LOG.info("Reported SQL Review issue {} on {}:{} (source XML: {}:{})",
                    ruleKey, targetFile.relativePath(), issueLine, stmt.getFilePath(), stmt.getLineNumber());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to report SQL Review issue {} on {}:{}",
                    ruleKey, targetFile.relativePath(), issueLine, e);
            return false;
        }
    }

    private int findJavaMethodLine(InputFile javaFile, String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return -1;
        }

        Pattern methodPattern = Pattern.compile("^\\s*(?:public\\s+|default\\s+|static\\s+|abstract\\s+)*"
                + "(?:[\\w<>\\[\\],.?]+\\s+)+" + Pattern.quote(methodName) + "\\s*\\(");

        try {
            String[] lines = javaFile.contents().split("\\R", -1);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                if (methodPattern.matcher(lines[lineIndex]).find()) {
                    return lineIndex + 1;
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to read Java mapper file for method detection: {}", javaFile.relativePath(), e);
        }
        return -1;
    }
}
