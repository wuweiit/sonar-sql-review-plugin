package com.yourcompany.sqlreview.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaRegistry 单元测试（含降级逻辑）
 *
 * @author marker
 */
class SchemaRegistryTest {

    private SchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SchemaRegistry();
        Path rootDir = Paths.get("src/test/resources/schemas");
        registry.loadFromDirectory(rootDir, "production", "dev");
    }

    // --- 基础加载 ---

    @Test
    void load_shouldParsePrimaryTables() {
        assertThat(registry.getTable("app_user")).isNotNull();
        assertThat(registry.getTable("app_user").getDatabase()).isEqualTo("app_production");
        assertThat(registry.getTable("app_user").getEnvironment()).isEqualTo("production");
    }

    @Test
    void load_shouldParseFallbackTables() {
        assertThat(registry.getTable("app_new_feature")).isNotNull();
        assertThat(registry.getTable("app_new_feature").getDatabase()).isEqualTo("app_dev");
    }

    @Test
    void load_shouldCountTables() {
        assertThat(registry.getPrimaryTableCount()).isEqualTo(4); // app_user + app_article + app_article_category + order_info
        assertThat(registry.getFallbackTableCount()).isEqualTo(1); // app_new_feature
    }

    // --- 多库支持 ---

    @Test
    void load_shouldLoadTablesFromMultipleDatabases() {
        // app_production 库
        assertThat(registry.getTable("app_user")).isNotNull();
        assertThat(registry.getTable("app_user").getDatabase()).isEqualTo("app_production");
        // order_production 库（第二个库）
        assertThat(registry.getTable("order_info")).isNotNull();
        assertThat(registry.getTable("order_info").getDatabase()).isEqualTo("order_production");
        assertThat(registry.getTable("order_info").getRowCount()).isEqualTo(500000L);
    }

    @Test
    void hasIndex_secondDatabase_checksCorrectly() {
        assertThat(registry.hasIndex("order_info", "user_id")).isTrue();
        assertThat(registry.hasIndex("order_info", "order_no")).isTrue();
        assertThat(registry.hasIndex("order_info", "status")).isFalse();
    }

    // --- 降级逻辑（核心） ---

    @Test
    void getTable_existsInPrimary_returnsPrimary() {
        TableMetadata meta = registry.getTable("app_user");
        assertThat(meta.getEnvironment()).isEqualTo("production");
        assertThat(meta.getRowCount()).isEqualTo(150000L);
    }

    @Test
    void getTable_notInPrimary_fallsBackToDev() {
        // app_new_feature 只存在于 dev
        TableMetadata meta = registry.getTable("app_new_feature");
        assertThat(meta).isNotNull();
        assertThat(meta.getEnvironment()).isEqualTo("dev");
        assertThat(meta.getDatabase()).isEqualTo("app_dev");
    }

    @Test
    void getTable_notInAnyEnv_returnsNull() {
        assertThat(registry.getTable("nonexistent_table")).isNull();
    }

    @Test
    void getSourceEnv_primaryTable() {
        assertThat(registry.getSourceEnv("app_user")).isEqualTo("production");
    }

    @Test
    void getSourceEnv_fallbackTable() {
        assertThat(registry.getSourceEnv("app_new_feature")).isEqualTo("dev");
    }

    @Test
    void getSourceEnv_unknownTable() {
        assertThat(registry.getSourceEnv("nonexistent")).isEqualTo("unknown");
    }

    // --- 索引检查 ---

    @Test
    void hasIndex_existingIndex_returnsTrue() {
        assertThat(registry.hasIndex("app_user", "user_id")).isTrue();
        assertThat(registry.hasIndex("app_user", "user_name")).isTrue();
        assertThat(registry.hasIndex("app_user", "email")).isTrue();
    }

    @Test
    void hasIndex_missingIndex_returnsFalse() {
        // create_time 不是任何索引的最左列（在复合索引 idx_status_create_time 中是第二列）
        assertThat(registry.hasIndex("app_user", "create_time")).isFalse();
    }

    @Test
    void hasIndex_fallbackTable_checksFallbackSchema() {
        // app_new_feature 只在 dev，status 无索引
        assertThat(registry.hasIndex("app_new_feature", "status")).isFalse();
        assertThat(registry.hasIndex("app_new_feature", "id")).isTrue(); // PRIMARY
    }

    @Test
    void hasIndex_unknownTable_returnsTrue() {
        // 表不存在时返回 true（跳过检查，避免误报）
        assertThat(registry.hasIndex("nonexistent_table", "any")).isTrue();
    }

    // --- 复合索引检查 ---

    @Test
    void hasIndex_compositeIndex_leftmostColumn() {
        // status 是复合索引 idx_status_create_time(status, create_time) 的最左列
        assertThat(registry.hasIndex("app_user", "status")).isTrue();
    }

    @Test
    void hasIndex_compositeIndex_nonLeftmostColumn() {
        // create_time 不是最左列，不能单独使用此复合索引
        assertThat(registry.hasIndex("app_user", "create_time")).isFalse();
    }

    @Test
    void hasCompositeIndexCoverage_matchingLeftmost() {
        // 条件包含 status（最左列）→ 可用
        assertThat(registry.hasCompositeIndexCoverage("app_user", List.of("status", "user_name")))
                .isTrue();
    }

    @Test
    void hasCompositeIndexCoverage_onlyNonLeftmost() {
        // 条件只含 create_time（非最左列）→ 不可用
        assertThat(registry.hasCompositeIndexCoverage("app_user", List.of("create_time")))
                .isFalse();
    }

    @Test
    void hasCompositeIndexCoverage_emptyConditions() {
        assertThat(registry.hasCompositeIndexCoverage("app_user", List.of()))
                .isFalse();
        assertThat(registry.hasCompositeIndexCoverage("app_user", null))
                .isFalse();
    }

    @Test
    void hasCompositeIndexCoverage_unknownTable() {
        // 表不存在时返回 true（避免误报）
        assertThat(registry.hasCompositeIndexCoverage("nonexistent", List.of("any")))
                .isTrue();
    }

    // --- 行数 ---

    @Test
    void getRowCount_primaryTable() {
        assertThat(registry.getRowCount("app_user")).isEqualTo(150000L);
    }

    @Test
    void getRowCount_fallbackTable() {
        assertThat(registry.getRowCount("app_new_feature")).isEqualTo(100L);
    }

    @Test
    void getRowCount_unknownTable_returnsZero() {
        assertThat(registry.getRowCount("nonexistent")).isEqualTo(0L);
    }

    // --- Entity 映射 ---

    @Test
    void resolveEntityToTable_withMapping() {
        assertThat(registry.resolveEntityToTable("AppUserEntity")).isEqualTo("app_user");
        assertThat(registry.resolveEntityToTable("AppNewFeatureEntity")).isEqualTo("app_new_feature");
        assertThat(registry.resolveEntityToTable("AppArticleEntity")).isEqualTo("app_article");
    }

    @Test
    void resolveEntityToTable_fallbackToConvention() {
        // 不在 entity_table_map.json 中，按命名约定转换
        assertThat(registry.resolveEntityToTable("SomeUnknownEntity"))
                .isEqualTo("some_unknown");
    }

    // --- 列信息 ---

    @Test
    void getTable_columnsLoaded() {
        TableMetadata meta = registry.getTable("app_user");
        assertThat(meta.getColumns()).hasSize(5);
        assertThat(meta.getColumns().get(0).getName()).isEqualTo("user_id");
        assertThat(meta.getColumns().get(0).getType()).isEqualTo("bigint");
        assertThat(meta.getColumns().get(0).isNullable()).isFalse();
    }

    @Test
    void getTable_indexesLoaded() {
        TableMetadata meta = registry.getTable("app_user");
        assertThat(meta.getIndexes()).hasSize(4);
        assertThat(meta.getIndexes().get(0).getName()).isEqualTo("PRIMARY");
        assertThat(meta.getIndexes().get(0).isUnique()).isTrue();
    }
}
