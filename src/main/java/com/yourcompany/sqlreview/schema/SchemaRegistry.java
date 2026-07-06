package com.yourcompany.sqlreview.schema;

import com.google.gson.Gson;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import com.yourcompany.sqlreview.util.NameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Schema 元信息注册表，支持主环境 + 降级环境查找
 *
 * @author marker
 */
public class SchemaRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistry.class);
    private static final Gson GSON = new Gson();

    // 单例（供 Java AST Visitor 使用）
    private static SchemaRegistry instance;

    private final Map<String, TableMetadata> primaryTables = new HashMap<>();
    private final Map<String, TableMetadata> fallbackTables = new HashMap<>();
    private final Map<String, String> entityTableMap = new HashMap<>();

    private String primaryEnv;
    private String fallbackEnv;

    public static SchemaRegistry getInstance() {
        if (instance == null) {
            instance = new SchemaRegistry();
        }
        return instance;
    }

    public static void setInstance(SchemaRegistry registry) {
        instance = registry;
    }

    /**
     * 从 SonarQube 配置加载 Schema
     */
    public void load(Configuration config) {
        String rootPath = config.get(SqlReviewProperties.SCHEMA_PATH_KEY)
                .orElse(SqlReviewProperties.SCHEMA_PATH_DEFAULT);
        primaryEnv = config.get(SqlReviewProperties.PRIMARY_ENV_KEY)
                .orElse(SqlReviewProperties.PRIMARY_ENV_DEFAULT);
        fallbackEnv = config.get(SqlReviewProperties.FALLBACK_ENV_KEY)
                .orElse(SqlReviewProperties.FALLBACK_ENV_DEFAULT);

        loadFromPaths(rootPath, primaryEnv, fallbackEnv);
    }

    /**
     * 从指定路径加载 Schema（供独立工具使用）
     */
    public void loadFromPaths(String rootPath, String primaryEnvName, String fallbackEnvName) {
        this.primaryEnv = primaryEnvName;
        this.fallbackEnv = fallbackEnvName;

        // 加载主环境
        Path primaryDir = Paths.get(rootPath, primaryEnvName);
        if (Files.isDirectory(primaryDir)) {
            loadFromDirectory(primaryDir, primaryTables);
            LOG.info("Loaded {} tables from primary env '{}'", primaryTables.size(), primaryEnv);
        } else {
            LOG.warn("Primary schema directory not found: {}", primaryDir);
        }

        // 加载降级环境
        Path fallbackDir = Paths.get(rootPath, fallbackEnvName);
        if (Files.isDirectory(fallbackDir)) {
            loadFromDirectory(fallbackDir, fallbackTables);
            LOG.info("Loaded {} tables from fallback env '{}'", fallbackTables.size(), fallbackEnv);
        } else {
            LOG.warn("Fallback schema directory not found: {}", fallbackDir);
        }

        // 加载 Entity 映射
        Path mapFile = Paths.get(rootPath, "entity_table_map.json");
        if (Files.exists(mapFile)) {
            loadEntityTableMap(mapFile);
        }

        setInstance(this);
    }

    /**
     * 从指定目录加载 Schema（供测试使用）
     */
    public void loadFromDirectory(Path rootDir, String primaryEnvName, String fallbackEnvName) {
        this.primaryEnv = primaryEnvName;
        this.fallbackEnv = fallbackEnvName;

        Path primaryDir = rootDir.resolve(primaryEnvName);
        if (Files.isDirectory(primaryDir)) {
            loadFromDirectory(primaryDir, primaryTables);
        }

        Path fallbackDir = rootDir.resolve(fallbackEnvName);
        if (Files.isDirectory(fallbackDir)) {
            loadFromDirectory(fallbackDir, fallbackTables);
        }

        Path mapFile = rootDir.resolve("entity_table_map.json");
        if (Files.exists(mapFile)) {
            loadEntityTableMap(mapFile);
        }

        setInstance(this);
    }

    private void loadFromDirectory(Path dir, Map<String, TableMetadata> target) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            TableMetadata meta = GSON.fromJson(
                                    Files.readString(p), TableMetadata.class);
                            if (meta != null && meta.getTable() != null) {
                                target.put(meta.getTable(), meta);
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to read schema file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOG.error("Failed to list schema directory: {}", dir, e);
        }
    }

    private void loadEntityTableMap(Path mapFile) {
        try {
            String content = Files.readString(mapFile);
            @SuppressWarnings("unchecked")
            Map<String, String> map = GSON.fromJson(content, Map.class);
            if (map != null) {
                entityTableMap.putAll(map);
                LOG.info("Loaded {} entity-table mappings", map.size());
            }
        } catch (IOException e) {
            LOG.error("Failed to read entity_table_map.json: {}", mapFile, e);
        }
    }

    /**
     * 获取表元数据（含降级逻辑）
     */
    public TableMetadata getTable(String tableName) {
        TableMetadata meta = primaryTables.get(tableName);
        if (meta != null) return meta;

        meta = fallbackTables.get(tableName);
        if (meta != null) {
            LOG.debug("Table '{}' not found in '{}', using fallback '{}'",
                    tableName, primaryEnv, fallbackEnv);
        }
        return meta;
    }

    /**
     * 检查指定列是否为某个索引的最左列（最左前缀原则）
     * <p>
     * 例如索引 (user_id, status)，只有 user_id 作为最左列才会生效。
     * 单独查询 status 不算“有索引”。
     * </p>
     */
    public boolean hasIndex(String table, String column) {
        TableMetadata meta = getTable(table);
        if (meta == null) return true; // 表不存在则跳过检查
        return meta.getIndexes().stream()
                .anyMatch(idx -> !idx.getColumns().isEmpty()
                        && idx.getColumns().get(0).equalsIgnoreCase(column));
    }

    /**
     * 检查给定条件列集合是否能覆盖某个索引的最左前缀（复合索引检查）
     * <p>
     * 从左到右匹配索引列，如果最左列不在条件中则索引不可用。
     * </p>
     * <ul>
     *   <li>索引 (a, b, c) + 条件 {a, b, d} → 前缀 a,b 匹配 ✓</li>
     *   <li>索引 (a, b, c) + 条件 {b, c} → 最左列 a 缺失 ✗</li>
     *   <li>索引 (a) + 条件 {a, b} → 前缀 a 匹配 ✓</li>
     * </ul>
     *
     * @param table            表名
     * @param conditionColumns WHERE 条件列集合
     * @return true 表示至少有一个索引的最左前缀被条件列覆盖
     */
    public boolean hasCompositeIndexCoverage(String table, List<String> conditionColumns) {
        if (conditionColumns == null || conditionColumns.isEmpty()) return false;
        TableMetadata meta = getTable(table);
        if (meta == null) return true;

        for (var idx : meta.getIndexes()) {
            List<String> idxCols = idx.getColumns();
            if (idxCols.isEmpty()) continue;

            // 最左列不在条件中 → 此索引完全不可用，跳过
            String leftmostCol = idxCols.get(0);
            if (conditionColumns.stream().noneMatch(c -> c.equalsIgnoreCase(leftmostCol))) {
                continue;
            }

            // 最左列匹配 → 索引可用（已覆盖最左前缀）
            return true;
        }
        return false;
    }

    public long getRowCount(String table) {
        TableMetadata meta = getTable(table);
        return meta != null ? meta.getRowCount() : 0L;
    }

    /**
     * 获取表的数据来源环境
     */
    public String getSourceEnv(String tableName) {
        if (primaryTables.containsKey(tableName)) return primaryEnv;
        if (fallbackTables.containsKey(tableName)) return fallbackEnv;
        return "unknown";
    }

    public String resolveEntityToTable(String entityClassName) {
        return entityTableMap.getOrDefault(entityClassName,
                NameConverter.entityClassToTable(entityClassName));
    }

    public int getPrimaryTableCount() {
        return primaryTables.size();
    }

    public int getFallbackTableCount() {
        return fallbackTables.size();
    }
}
