package com.yourcompany.sqlreview.sensor;

import com.yourcompany.sqlreview.parser.MyBatisXmlParser;
import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.rules.DynamicConcatRule;
import com.yourcompany.sqlreview.rules.FullTableScanRule;
import com.yourcompany.sqlreview.rules.IndexFunctionRule;
import com.yourcompany.sqlreview.rules.Issue;
import com.yourcompany.sqlreview.rules.LikeLeadingWildcardRule;
import com.yourcompany.sqlreview.rules.NoIndexWhereRule;
import com.yourcompany.sqlreview.rules.NoLimitLargeTableRule;
import com.yourcompany.sqlreview.rules.SelectStarXmlRule;
import com.yourcompany.sqlreview.rules.SqlRulesDefinition;
import com.yourcompany.sqlreview.rules.SqlXmlRule;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import com.yourcompany.sqlreview.tool.SqlDumpWriter;
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

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis XML SQL Review Sensor.
 */
public class MyBatisXmlSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisXmlSensor.class);

    private final List<SqlXmlRule> rules = new ArrayList<>();

    private final SqlDumpWriter sqlDumpWriter = new SqlDumpWriter();

    public MyBatisXmlSensor() {
        // 默认构造，execute() 中重新初始化规则
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

        SchemaRegistry schema = new SchemaRegistry();
        schema.load(context.config());
        LOG.info("Schema loaded: {} primary tables, {} fallback tables",
                schema.getPrimaryTableCount(), schema.getFallbackTableCount());

        // 读取配置的阈值
        long largeTableThreshold = context.config()
                .getLong(SqlReviewProperties.LARGE_TABLE_THRESHOLD_KEY)
                .orElse((long) SqlReviewProperties.LARGE_TABLE_THRESHOLD_DEFAULT);
        long noLimitThreshold = context.config()
                .getLong(SqlReviewProperties.NO_LIMIT_THRESHOLD_KEY)
                .orElse((long) SqlReviewProperties.NO_LIMIT_THRESHOLD_DEFAULT);
        LOG.info("Thresholds: large-table={}, no-limit={}", largeTableThreshold, noLimitThreshold);

        // 初始化规则（传入配置的阈值）
        rules.clear();
        rules.add(new SelectStarXmlRule());
        rules.add(new NoIndexWhereRule(largeTableThreshold));
        rules.add(new FullTableScanRule(largeTableThreshold));
        rules.add(new LikeLeadingWildcardRule());
        rules.add(new NoLimitLargeTableRule(noLimitThreshold));
        rules.add(new DynamicConcatRule());
        rules.add(new IndexFunctionRule());

        logRuleActivation(context);

        FileSystem fs = context.fileSystem();
        int fileCount = 0;
        int issueCount = 0;

        for (InputFile xmlFile : fs.inputFiles(fs.predicates().hasExtension("xml"))) {
            List<SqlStatement> statements = MyBatisXmlParser.parse(xmlFile);
            if (statements.isEmpty()) {
                continue;
            }

            fileCount++;
            LOG.info("Parsed {} SQL statements from {} (lang={}, type={}, status={})",
                    statements.size(), xmlFile.filename(),
                    xmlFile.language(), xmlFile.type(), xmlFile.status());

            for (SqlStatement statement : statements) {
                // 输出 SQL 到日志和收集器
                sqlDumpWriter.addXmlSql(xmlFile.relativePath(), statement);
                for (SqlXmlRule rule : rules) {
                    List<Issue> issues = rule.check(statement, schema);
                    for (Issue issue : issues) {
                        if (!isRuleActive(context, issue.getRuleId())) {
                            LOG.debug("Skipping inactive SQL Review rule {}", issue.getRuleId());
                            continue;
                        }
                        if (reportIssue(context, xmlFile, statement, issue)) {
                            issueCount++;
                        }
                    }
                }
            }
        }

        LOG.info("MyBatis XML SQL Review complete: {} files analyzed, {} issues reported, {} SQL statements collected",
                fileCount, issueCount, sqlDumpWriter.size());

        // 输出 SQL 到文件
        String dumpFile = context.config().get(SqlReviewProperties.SQL_DUMP_FILE_KEY).orElse(null);
        sqlDumpWriter.dumpToFile(dumpFile);
    }

    private void logRuleActivation(SensorContext context) {
        String[] ruleIds = {
            SqlRulesDefinition.SQL_001, SqlRulesDefinition.SQL_002, SqlRulesDefinition.SQL_003,
            SqlRulesDefinition.SQL_004, SqlRulesDefinition.SQL_101, SqlRulesDefinition.SQL_103,
            SqlRulesDefinition.SQL_201
        };
        for (String ruleId : ruleIds) {
            RuleKey key = RuleKey.of(SqlRulesDefinition.REPO_KEY, ruleId);
            ActiveRule activeRule = context.activeRules().find(key);
            if (activeRule == null) {
                LOG.warn("Rule {} is NOT active in this project's XML quality profile", key);
            } else {
                LOG.info("Rule {} is ACTIVE (severity={})", key, activeRule.severity());
            }
        }
    }

    private boolean isRuleActive(SensorContext context, String ruleId) {
        return context.activeRules().find(RuleKey.of(SqlRulesDefinition.REPO_KEY, ruleId)) != null;
    }

    private boolean reportIssue(SensorContext context, InputFile xmlFile, SqlStatement statement, Issue issue) {
        RuleKey ruleKey = RuleKey.of(SqlRulesDefinition.REPO_KEY, issue.getRuleId());
        int issueLine = Math.max(1, statement.getLineNumber());

        try {
            NewIssue newIssue = context.newIssue();
            NewIssueLocation location = newIssue.newLocation()
                    .on(xmlFile)
                    .at(xmlFile.selectLine(issueLine))
                    .message("[SQL Review] " + issue.getMessage());

            newIssue.forRule(ruleKey)
                    .at(location)
                    .save();
            LOG.info("Reported SQL Review issue {} on {}:{}",
                    ruleKey, xmlFile.relativePath(), issueLine);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to report SQL Review issue {} on {}:{}",
                    ruleKey, xmlFile.relativePath(), issueLine, e);
            return false;
        }
    }
}
