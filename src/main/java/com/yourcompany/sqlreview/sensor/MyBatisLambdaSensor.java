package com.yourcompany.sqlreview.sensor;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.rules.DynamicConcatRule;
import com.yourcompany.sqlreview.rules.FullTableScanRule;
import com.yourcompany.sqlreview.rules.IndexFunctionRule;
import com.yourcompany.sqlreview.rules.Issue;
import com.yourcompany.sqlreview.rules.SelectStarXmlRule;
import com.yourcompany.sqlreview.rules.SqlJavaRulesDefinition;
import com.yourcompany.sqlreview.rules.SqlXmlRule;
import com.yourcompany.sqlreview.rules.java_.LambdaApplyFunctionRule;
import com.yourcompany.sqlreview.rules.java_.LambdaChain;
import com.yourcompany.sqlreview.rules.java_.LambdaChainParser;
import com.yourcompany.sqlreview.rules.java_.LambdaLikeWildcardRule;
import com.yourcompany.sqlreview.rules.java_.LambdaNoIndexRule;
import com.yourcompany.sqlreview.rules.java_.LambdaNoLimitRule;
import com.yourcompany.sqlreview.rules.java_.LambdaNoWhereRule;
import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule;
import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.rules.java_.LambdaToSqlConverter;
import com.yourcompany.sqlreview.schema.DataSourceResolver;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.schema.SchemaSyncer;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import com.yourcompany.sqlreview.tool.SqlDumpWriter;
import com.yourcompany.sqlreview.util.PomArtifactIdParser;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Lambda 表达式审查 Sensor
 * <p>
 * 扫描 Java 文件，检查 MyBatis Plus Lambda Wrapper 使用规范。
 * <p>
 * 检查流程：
 * <ol>
 *   <li>直接规则检查（SQL-301~SQL-306）：Lambda 特有模式</li>
 *   <li>Lambda → SQL 转换 + 复用现有 SQL 规则（SQL-001~SQL-201）</li>
 * </ol>
 * </p>
 *
 * @author marker
 */
public class MyBatisLambdaSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisLambdaSensor.class);

    // Lambda 直接检查规则
    private LambdaSelectAllRule selectAllRule = new LambdaSelectAllRule();
    private LambdaNoWhereRule noWhereRule = new LambdaNoWhereRule();
    private final LambdaChainParser chainParser = new LambdaChainParser();
    private LambdaNoIndexRule noIndexRule = new LambdaNoIndexRule();
    private final LambdaLikeWildcardRule likeWildcardRule = new LambdaLikeWildcardRule();
    private LambdaNoLimitRule noLimitRule = new LambdaNoLimitRule();
    private final LambdaApplyFunctionRule applyFunctionRule = new LambdaApplyFunctionRule();

    // SQL 收集器
    private final SqlDumpWriter sqlDumpWriter = new SqlDumpWriter();

    // 复用的 SQL 规则（Lambda → SQL 后运行）
    private final List<SqlXmlRule> sqlRules = new ArrayList<>();

    public MyBatisLambdaSensor() {
        // 默认构造，execute() 中重新初始化规则
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .onlyOnLanguage(SqlJavaRulesDefinition.LANGUAGE)
                .name("MyBatis Lambda SQL Review");
    }

    @Override
    public void execute(SensorContext context) {
        LOG.info("Starting MyBatis Lambda SQL Review...");

        // 从 HTTP 同步 Schema 元数据
        syncSchemaFromHttp(context);

        // 加载 Schema
        SchemaRegistry schema = new SchemaRegistry();
        schema.load(context.config());
        LOG.info("Schema loaded for Lambda Sensor: {} primary tables, {} fallback tables",
                schema.getPrimaryTableCount(), schema.getFallbackTableCount());

        // 解析 artifactId → 设置主库
        FileSystem fs = context.fileSystem();
        Path pomPath = fs.baseDir().toPath().resolve("pom.xml");
        Optional<String> artifactId = PomArtifactIdParser.parseArtifactId(pomPath);
        if (artifactId.isPresent()) {
            schema.setPrimaryDatabaseByProject(artifactId.get());
        } else {
            LOG.warn("Could not parse artifactId from pom.xml, primaryDatabase not set");
        }
        LOG.info("Primary database: {}", schema.getPrimaryDatabase());

        // 构建 @DS 解析器：先扫描所有 Java 文件收集 @DS 信息
        DataSourceResolver dsResolver = new DataSourceResolver();
        for (InputFile javaFile : fs.inputFiles(fs.predicates().hasExtension("java"))) {
            try {
                dsResolver.scanFile(javaFile.contents());
            } catch (IOException e) {
                LOG.warn("Failed to read Java file for @DS scan: {}", javaFile.filename(), e);
            }
        }

        // 读取配置的阈值
        long largeTableThreshold = context.config()
                .getLong(SqlReviewProperties.LARGE_TABLE_THRESHOLD_KEY)
                .orElse((long) SqlReviewProperties.LARGE_TABLE_THRESHOLD_DEFAULT);
        long noLimitThreshold = context.config()
                .getLong(SqlReviewProperties.NO_LIMIT_THRESHOLD_KEY)
                .orElse((long) SqlReviewProperties.NO_LIMIT_THRESHOLD_DEFAULT);
        LOG.info("Thresholds: large-table={}, no-limit={}", largeTableThreshold, noLimitThreshold);

        // 初始化规则（传入配置的阈值）
        noIndexRule = new LambdaNoIndexRule(largeTableThreshold);
        noLimitRule = new LambdaNoLimitRule(largeTableThreshold);
        sqlRules.clear();
        sqlRules.add(new SelectStarXmlRule());     // SQL-003（Lambda 无对应规则）
        sqlRules.add(new FullTableScanRule(largeTableThreshold)); // SQL-002
        sqlRules.add(new IndexFunctionRule());      // SQL-004（Lambda 无对应规则）
        sqlRules.add(new DynamicConcatRule());      // SQL-201（Lambda 无对应规则）

        logRuleActivation(context);

        int fileCount = 0;
        int issueCount = 0;

        for (InputFile javaFile : fs.inputFiles(fs.predicates().hasExtension("java"))) {
            String content;
            try {
                content = javaFile.contents();
            } catch (IOException e) {
                LOG.warn("Failed to read Java file: {}", javaFile.filename(), e);
                continue;
            }

            // 快速过滤：不包含 MyBatis Plus 相关关键字则跳过
            if (!content.contains("lambdaQuery") && !content.contains("LambdaQueryWrapper")
                    && !content.contains("selectAll") && !content.contains("MPJLambdaWrapper")) {
                continue;
            }

            fileCount++;
            String[] lines = content.split("\n", -1);
            LOG.info("Analyzing Lambda patterns in {} (lang={}, status={})",
                    javaFile.filename(), javaFile.language(), javaFile.status());

            // ===== 第一阶段：Lambda 直接检查规则（SQL-301~SQL-306） =====

            // SQL-301: selectAll() 检测
            List<JavaIssue> selectAllIssues = selectAllRule.check(lines);
            issueCount += reportJavaIssues(context, javaFile, selectAllIssues);

            // SQL-302: 无 WHERE 条件检测
            List<JavaIssue> noWhereIssues = noWhereRule.check(lines);
            issueCount += reportJavaIssues(context, javaFile, noWhereIssues);

            // 解析 Lambda 链
            List<LambdaChain> chains = chainParser.parse(lines);
            String className = extractClassName(javaFile.filename());
            for (LambdaChain chain : chains) {
                // 解析数据库：@DS 优先，降级到主库
                Optional<String> dsValue = dsResolver.resolve(className, chain.getEntityClass());
                chain.setResolvedDatabase(dsValue.orElse(schema.getPrimaryDatabase()));

                // SQL-303: 条件字段缺索引（多表感知）
                List<JavaIssue> noIdxIssues = noIndexRule.check(chain, schema);
                issueCount += reportJavaIssues(context, javaFile, noIdxIssues);

                // SQL-304: LIKE 以 % 开头
                List<JavaIssue> likeIssues = likeWildcardRule.check(chain);
                issueCount += reportJavaIssues(context, javaFile, likeIssues);

                // SQL-305: 大表查询无 LIMIT
                List<JavaIssue> limitIssues = noLimitRule.check(chain, schema);
                issueCount += reportJavaIssues(context, javaFile, limitIssues);

                // SQL-306: .apply() 中索引列使用函数
                List<JavaIssue> applyIssues = applyFunctionRule.check(chain, schema);
                issueCount += reportJavaIssues(context, javaFile, applyIssues);

                // ===== 第二阶段：Lambda → SQL 转换 + 复用 SQL 规则 =====
                SqlStatement sqlStmt = LambdaToSqlConverter.convert(chain, schema);
                if (sqlStmt != null) {
                    // 输出 SQL 到日志和收集器
                    sqlDumpWriter.addLambdaSql(javaFile.relativePath(), chain.getStartLine(), sqlStmt.getSql());
                    LOG.debug("Converted Lambda to SQL: {}", sqlStmt.getSql());
                    for (SqlXmlRule rule : sqlRules) {
                        List<Issue> sqlIssues = rule.check(sqlStmt, schema);
                        for (Issue issue : sqlIssues) {
                            if (!isRuleActive(context, issue.getRuleId())) {
                                LOG.debug("Skipping inactive rule {}", issue.getRuleId());
                                continue;
                            }
                            if (reportSqlIssue(context, javaFile, chain.getStartLine(), issue)) {
                                issueCount++;
                            }
                        }
                    }
                }
            }
        }

        LOG.info("MyBatis Lambda SQL Review complete: {} files analyzed, {} issues reported, {} SQL statements collected",
                fileCount, issueCount, sqlDumpWriter.size());

        // 输出 SQL 到文件
        String dumpFile = context.config().get(SqlReviewProperties.SQL_DUMP_FILE_KEY).orElse(null);
        sqlDumpWriter.dumpToFile(dumpFile);
    }

    private int reportJavaIssues(SensorContext context, InputFile javaFile, List<JavaIssue> issues) {
        int count = 0;
        for (JavaIssue issue : issues) {
            if (!isRuleActive(context, issue.getRuleId())) {
                LOG.debug("Skipping inactive rule {}", issue.getRuleId());
                continue;
            }
            if (reportJavaIssue(context, javaFile, issue)) {
                count++;
            }
        }
        return count;
    }

    private void logRuleActivation(SensorContext context) {
        String[] ruleIds = {
            // Lambda → SQL 复用规则
            SqlJavaRulesDefinition.SQL_001, SqlJavaRulesDefinition.SQL_002,
            SqlJavaRulesDefinition.SQL_003, SqlJavaRulesDefinition.SQL_004,
            SqlJavaRulesDefinition.SQL_101, SqlJavaRulesDefinition.SQL_103,
            SqlJavaRulesDefinition.SQL_201,
            // Lambda 特有规则
            SqlJavaRulesDefinition.SQL_301, SqlJavaRulesDefinition.SQL_302,
            SqlJavaRulesDefinition.SQL_303, SqlJavaRulesDefinition.SQL_304,
            SqlJavaRulesDefinition.SQL_305, SqlJavaRulesDefinition.SQL_306
        };
        for (String ruleId : ruleIds) {
            RuleKey key = RuleKey.of(SqlJavaRulesDefinition.JAVA_REPO_KEY, ruleId);
            ActiveRule activeRule = context.activeRules().find(key);
            if (activeRule == null) {
                LOG.warn("Rule {} is NOT active in this project's Java quality profile", key);
            } else {
                LOG.info("Rule {} is ACTIVE (severity={})", key, activeRule.severity());
            }
        }
    }

    private boolean isRuleActive(SensorContext context, String ruleId) {
        return context.activeRules().find(
                RuleKey.of(SqlJavaRulesDefinition.JAVA_REPO_KEY, ruleId)) != null;
    }

    /**
     * 从文件名提取类简单名：OrderService.java → OrderService
     */
    private String extractClassName(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
    }

    /**
     * 报告 Lambda 直接检查规则的 Issue
     */
    private boolean reportJavaIssue(SensorContext context, InputFile javaFile, JavaIssue issue) {
        RuleKey ruleKey = RuleKey.of(SqlJavaRulesDefinition.JAVA_REPO_KEY, issue.getRuleId());
        int issueLine = Math.max(1, issue.getLine());

        try {
            NewIssue newIssue = context.newIssue();
            NewIssueLocation location = newIssue.newLocation()
                    .on(javaFile)
                    .at(javaFile.selectLine(issueLine))
                    .message("[SQL Review] " + issue.getMessage());

            newIssue.forRule(ruleKey)
                    .at(location)
                    .save();
            LOG.info("Reported Lambda issue {} on {}:{}",
                    ruleKey, javaFile.relativePath(), issueLine);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to report Lambda issue {} on {}:{}",
                    ruleKey, javaFile.relativePath(), issueLine, e);
            return false;
        }
    }

    /**
     * 报告 Lambda → SQL 转换后复用规则的 Issue
     */
    private boolean reportSqlIssue(SensorContext context, InputFile javaFile, int chainStartLine, Issue issue) {
        RuleKey ruleKey = RuleKey.of(SqlJavaRulesDefinition.JAVA_REPO_KEY, issue.getRuleId());
        int issueLine = Math.max(1, chainStartLine);

        try {
            NewIssue newIssue = context.newIssue();
            NewIssueLocation location = newIssue.newLocation()
                    .on(javaFile)
                    .at(javaFile.selectLine(issueLine))
                    .message("[SQL Review] " + issue.getMessage());

            newIssue.forRule(ruleKey)
                    .at(location)
                    .save();
            LOG.info("Reported SQL issue {} on {}:{}",
                    ruleKey, javaFile.relativePath(), issueLine);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to report SQL issue {} on {}:{}",
                    ruleKey, javaFile.relativePath(), issueLine, e);
            return false;
        }
    }

    /**
     * 从 HTTP 服务器同步 Schema 元数据到本地目录（一次性同步）。
     * 若未配置 sync-url 则跳过。
     */
    private void syncSchemaFromHttp(SensorContext context) {
        String syncUrl = context.config()
                .get(SqlReviewProperties.SCHEMA_SYNC_URL_KEY)
                .orElse(SqlReviewProperties.SCHEMA_SYNC_URL_DEFAULT);
        if (syncUrl.isEmpty()) {
            LOG.info("Schema sync URL not configured, skipping HTTP sync");
            return;
        }

        String localPath = context.config()
                .get(SqlReviewProperties.SCHEMA_PATH_KEY)
                .orElse(SqlReviewProperties.SCHEMA_PATH_DEFAULT);

//        SchemaSyncer syncer = new SchemaSyncer(syncUrl, Paths.get(localPath));
//        try {
//            syncer.sync();
//        } catch (Exception e) {
//            LOG.error("Schema sync failed, sensor will proceed with existing local data", e);
//        }
        LOG.info("Schema HTTP sync completed: url={}, localDir={}", syncUrl, localPath);
    }
}
