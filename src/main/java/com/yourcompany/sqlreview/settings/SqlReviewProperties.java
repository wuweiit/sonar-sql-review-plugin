package com.yourcompany.sqlreview.settings;

import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.PropertyType;

import java.util.List;

/**
 * 插件全局配置项定义
 *
 * @author marker
 */
public final class SqlReviewProperties {

    public static final String SCHEMA_PATH_KEY = "sonar.sql-review.schema-path";
    public static final String SCHEMA_PATH_DEFAULT = "/opt/sonar-schemas";

    public static final String PRIMARY_ENV_KEY = "sonar.sql-review.primary-env";
    public static final String PRIMARY_ENV_DEFAULT = "production";

    public static final String FALLBACK_ENV_KEY = "sonar.sql-review.fallback-env";
    public static final String FALLBACK_ENV_DEFAULT = "dev";

    public static final String LARGE_TABLE_THRESHOLD_KEY = "sonar.sql-review.large-table-threshold";
    public static final int LARGE_TABLE_THRESHOLD_DEFAULT = 10000;

    public static final String NO_LIMIT_THRESHOLD_KEY = "sonar.sql-review.no-limit-threshold";
    public static final int NO_LIMIT_THRESHOLD_DEFAULT = 50000;

    public static final String MAX_JOINS_KEY = "sonar.sql-review.max-joins";
    public static final int MAX_JOINS_DEFAULT = 3;

    public static final String SQL_DUMP_FILE_KEY = "sonar.sql-review.sql-dump-file";
    public static final String SQL_DUMP_FILE_DEFAULT = "";

    public static final String CATEGORY = "SQL Review";

    private SqlReviewProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
                PropertyDefinition.builder(SCHEMA_PATH_KEY)
                        .name("Schema Root Directory")
                        .description("Schema 根目录，子目录按环境名组织")
                        .defaultValue(SCHEMA_PATH_DEFAULT)
                        .category(CATEGORY)
                        .subCategory("Schema")
                        .build(),
                PropertyDefinition.builder(PRIMARY_ENV_KEY)
                        .name("Primary Environment")
                        .description("主环境名称（优先查找），对应根目录下的子目录名")
                        .defaultValue(PRIMARY_ENV_DEFAULT)
                        .category(CATEGORY)
                        .subCategory("Schema")
                        .build(),
                PropertyDefinition.builder(FALLBACK_ENV_KEY)
                        .name("Fallback Environment")
                        .description("降级环境名称（主环境找不到时回退），对应根目录下的子目录名")
                        .defaultValue(FALLBACK_ENV_DEFAULT)
                        .category(CATEGORY)
                        .subCategory("Schema")
                        .build(),
                PropertyDefinition.builder(LARGE_TABLE_THRESHOLD_KEY)
                        .name("Large Table Threshold")
                        .description("大表行数阈值，超过此行数的表将启用全表扫描检查")
                        .defaultValue(String.valueOf(LARGE_TABLE_THRESHOLD_DEFAULT))
                        .type(PropertyType.INTEGER)
                        .category(CATEGORY)
                        .subCategory("Thresholds")
                        .build(),
                PropertyDefinition.builder(NO_LIMIT_THRESHOLD_KEY)
                        .name("No Limit Threshold")
                        .description("触发无 LIMIT 告警的表行数阈值")
                        .defaultValue(String.valueOf(NO_LIMIT_THRESHOLD_DEFAULT))
                        .type(PropertyType.INTEGER)
                        .category(CATEGORY)
                        .subCategory("Thresholds")
                        .build(),
                PropertyDefinition.builder(MAX_JOINS_KEY)
                        .name("Max Joins")
                        .description("单条 SQL 最大 JOIN 表数")
                        .defaultValue(String.valueOf(MAX_JOINS_DEFAULT))
                        .type(PropertyType.INTEGER)
                        .category(CATEGORY)
                        .subCategory("Thresholds")
                        .build(),
                PropertyDefinition.builder(SQL_DUMP_FILE_KEY)
                        .name("SQL Dump File")
                        .description("将扫描到的所有 SQL 语句输出到指定文件（留空则仅输出到日志）")
                        .defaultValue(SQL_DUMP_FILE_DEFAULT)
                        .category(CATEGORY)
                        .subCategory("Output")
                        .build()
        );
    }
}
