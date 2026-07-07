package com.yourcompany.sqlreview.schema;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import com.yourcompany.sqlreview.util.NameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Schema 元信息注册表，支持多数据库维度查找、主环境 + 降级环境降级、
 * 项目→主库映射（project_database_map.json）
 *
 * <p>内部存储结构：env → database → tableName → TableMetadata</p>
 *
 * @author marker
 */
public class SchemaRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistry.class);
    private static final Gson GSON = new Gson();

    // 单例（供 Java AST Visitor 使用）
    private static SchemaRegistry instance;

    /**
     * 主环境：database → tableName → TableMetadata
     */
    private final Map<String, Map<String, TableMetadata>> primaryTables = new LinkedHashMap<>();

    /**
     * 降级环境：database → tableName → TableMetadata
     */
    private final Map<String, Map<String, TableMetadata>> fallbackTables = new LinkedHashMap<>();

    /**
     * 实体类名 → 表名（向后兼容旧格式）
     */
    private final Map<String, String> entityTableMap = new HashMap<>();

    /**
     * artifactId → 主库名（来自 project_database_map.json）
     */
    private final Map<String, String> projectDatabaseMap = new HashMap<>();

    private String primaryEnv;
    private String fallbackEnv;

    /**
     * 当前项目主库名（由 artifactId 映射或 SonarQube 配置设置）
     */
    private String primaryDatabase;

    public static SchemaRegistry getInstance() {
        if (instance == null) {
            instance = new SchemaRegistry();
        }
        return instance;
    }

    public static void setInstance(SchemaRegistry registry) {
        instance = registry;
    }

    // ------------------------------------------------------------------ 加载

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

        Path root = Paths.get(rootPath);

        // 加载主环境
        Path primaryDir = root.resolve(primaryEnvName);
        if (Files.isDirectory(primaryDir)) {
            loadEnvDirectory(primaryDir, primaryTables);
            int total = countAll(primaryTables);
            LOG.info("Loaded {} tables from primary env '{}' across {} databases",
                    total, primaryEnv, primaryTables.size());
        } else {
            LOG.warn("Primary schema directory not found: {}", primaryDir);
        }

        // 加载降级环境
        Path fallbackDir = root.resolve(fallbackEnvName);
        if (Files.isDirectory(fallbackDir)) {
            loadEnvDirectory(fallbackDir, fallbackTables);
            int total = countAll(fallbackTables);
            LOG.info("Loaded {} tables from fallback env '{}' across {} databases",
                    total, fallbackEnv, fallbackTables.size());
        } else {
            LOG.warn("Fallback schema directory not found: {}", fallbackDir);
        }

        // 加载 Entity → Table 映射
        Path entityMapFile = root.resolve("entity_table_map.json");
        if (Files.exists(entityMapFile)) {
            loadEntityTableMap(entityMapFile);
        }

        // 加载 artifactId → 主库映射
        Path projectMapFile = root.resolve("project_database_map.json");
        if (Files.exists(projectMapFile)) {
            loadProjectDatabaseMap(projectMapFile);
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
            loadEnvDirectory(primaryDir, primaryTables);
        }

        Path fallbackDir = rootDir.resolve(fallbackEnvName);
        if (Files.isDirectory(fallbackDir)) {
            loadEnvDirectory(fallbackDir, fallbackTables);
        }

        Path entityMapFile = rootDir.resolve("entity_table_map.json");
        if (Files.exists(entityMapFile)) {
            loadEntityTableMap(entityMapFile);
        }

        Path projectMapFile = rootDir.resolve("project_database_map.json");
        if (Files.exists(projectMapFile)) {
            loadProjectDatabaseMap(projectMapFile);
        }

        setInstance(this);
    }

    /**
     * 扫描环境目录下的所有库子目录，按 database 分组加载表元数据（按目录名排序保证确定性）
     */
    private void loadEnvDirectory(Path envDir, Map<String, Map<String, TableMetadata>> envTarget) {
        try (Stream<Path> dbs = Files.list(envDir)) {
            List<Path> sortedDbs = dbs.filter(Files::isDirectory).sorted().toList();
            for (Path dbDir : sortedDbs) {
                String dbName = dbDir.getFileName().toString();
                Map<String, TableMetadata> dbTables =
                        envTarget.computeIfAbsent(dbName, k -> new HashMap<>());
                loadDbDirectory(dbDir, dbTables);
            }
        } catch (IOException e) {
            LOG.error("Failed to list env directory: {}", envDir, e);
        }
    }

    private void loadDbDirectory(Path dbDir, Map<String, TableMetadata> dbTables) {
        try (Stream<Path> files = Files.list(dbDir)) {
            files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            TableMetadata meta = GSON.fromJson(
                                    Files.readString(p), TableMetadata.class);
                            if (meta != null && meta.getTable() != null) {
                                dbTables.put(meta.getTable(), meta);
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to read schema file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOG.error("Failed to list db directory: {}", dbDir, e);
        }
    }

    private void loadEntityTableMap(Path mapFile) {
        try {
            String content = Files.readString(mapFile);
            JsonElement root = JsonParser.parseString(content);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                for (String key : obj.keySet()) {
                    JsonElement val = obj.get(key);
                    if (val.isJsonPrimitive()) {
                        // 旧格式：{"AppUserEntity": "app_user"}
                        entityTableMap.put(key, val.getAsString());
                    }
                    // 新格式扩展预留：{"AppUserEntity": {"table":"app_user","database":"app_production"}}
                }
                LOG.info("Loaded {} entity-table mappings", entityTableMap.size());
            }
        } catch (IOException e) {
            LOG.error("Failed to read entity_table_map.json: {}", mapFile, e);
        }
    }

    private void loadProjectDatabaseMap(Path mapFile) {
        try {
            String content = Files.readString(mapFile);
            @SuppressWarnings("unchecked")
            Map<String, String> map = GSON.fromJson(content, Map.class);
            if (map != null) {
                projectDatabaseMap.putAll(map);
                LOG.info("Loaded {} project→database mappings", map.size());
            }
        } catch (IOException e) {
            LOG.error("Failed to read project_database_map.json: {}", mapFile, e);
        }
    }

    // ------------------------------------------------------------------ 查找

    /**
     * 获取表元数据（向后兼容，遍历所有库查找）
     * <p>若已设置 primaryDatabase，优先在主库中查找。</p>
     */
    public TableMetadata getTable(String tableName) {
        return getTable(tableName, null);
    }

    /**
     * 获取表元数据（指定数据库维度）
     * <p>查找优先级：</p>
     * <ol>
     *   <li>若 database 非空：在指定库的 primary / fallback 中查找</li>
     *   <li>若已设置 primaryDatabase：在主库的 primary / fallback 中查找</li>
     *   <li>遍历所有库（primary 优先，fallback 次之）</li>
     * </ol>
     *
     * @param tableName 表名
     * @param database  数据库名，可为 null
     */
    public TableMetadata getTable(String tableName, String database) {
        // 1. 指定 database 查找
        if (database != null && !database.isEmpty()) {
            TableMetadata meta = findInEnv(primaryTables, database, tableName);
            if (meta != null) return meta;

            meta = findInEnv(fallbackTables, database, tableName);
            if (meta != null) {
                LOG.debug("Table '{}' not found in primary env db '{}', using fallback '{}'",
                        tableName, database, fallbackEnv);
                return meta;
            }
            // 指定库未找到，继续降级
        }

        // 2. primaryDatabase 查找（若设置且与 database 不同）
        if (primaryDatabase != null && !primaryDatabase.isEmpty()
                && !primaryDatabase.equals(database)) {
            TableMetadata meta = findInEnv(primaryTables, primaryDatabase, tableName);
            if (meta != null) return meta;

            meta = findInEnv(fallbackTables, primaryDatabase, tableName);
            if (meta != null) return meta;
        }

        // 3. 遍历所有库：primary env 优先
        TableMetadata meta = scanAllDatabases(primaryTables, tableName);
        if (meta != null) return meta;

        // 4. fallback env
        meta = scanAllDatabases(fallbackTables, tableName);
        if (meta != null) {
            LOG.debug("Table '{}' not found in primary env '{}', using fallback '{}'",
                    tableName, primaryEnv, fallbackEnv);
        }
        return meta;
    }

    private TableMetadata findInEnv(
            Map<String, Map<String, TableMetadata>> envData, String database, String tableName) {
        Map<String, TableMetadata> dbTables = envData.get(database);
        return dbTables != null ? dbTables.get(tableName) : null;
    }

    private TableMetadata scanAllDatabases(
            Map<String, Map<String, TableMetadata>> envData, String tableName) {
        for (Map<String, TableMetadata> dbTables : envData.values()) {
            TableMetadata meta = dbTables.get(tableName);
            if (meta != null) return meta;
        }
        return null;
    }

    // ------------------------------------------------------------------ 索引检查

    /**
     * 检查指定列是否为某个索引的最左列（向后兼容）
     */
    public boolean hasIndex(String table, String column) {
        return hasIndex(table, column, null);
    }

    /**
     * 检查指定列是否为某个索引的最左列（指定数据库维度）
     */
    public boolean hasIndex(String table, String column, String database) {
        TableMetadata meta = getTable(table, database);
        if (meta == null) return true; // 表不存在则跳过检查
        return meta.getIndexes().stream()
                .anyMatch(idx -> !idx.getColumns().isEmpty()
                        && idx.getColumns().get(0).equalsIgnoreCase(column));
    }

    /**
     * 复合索引覆盖检查（向后兼容）
     */
    public boolean hasCompositeIndexCoverage(String table, List<String> conditionColumns) {
        return hasCompositeIndexCoverage(table, conditionColumns, null);
    }

    /**
     * 复合索引覆盖检查（指定数据库维度）
     */
    public boolean hasCompositeIndexCoverage(String table, List<String> conditionColumns,
                                              String database) {
        if (conditionColumns == null || conditionColumns.isEmpty()) return false;
        TableMetadata meta = getTable(table, database);
        if (meta == null) return true;

        for (var idx : meta.getIndexes()) {
            List<String> idxCols = idx.getColumns();
            if (idxCols.isEmpty()) continue;

            String leftmostCol = idxCols.get(0);
            if (conditionColumns.stream().noneMatch(c -> c.equalsIgnoreCase(leftmostCol))) {
                continue;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 行数

    public long getRowCount(String table) {
        return getRowCount(table, null);
    }

    public long getRowCount(String table, String database) {
        TableMetadata meta = getTable(table, database);
        return meta != null ? meta.getRowCount() : 0L;
    }

    // ------------------------------------------------------------------ 环境/库信息

    /**
     * 获取表的数据来源环境
     */
    public String getSourceEnv(String tableName) {
        if (scanAllDatabases(primaryTables, tableName) != null) return primaryEnv;
        if (scanAllDatabases(fallbackTables, tableName) != null) return fallbackEnv;
        return "unknown";
    }

    /**
     * 返回所有已加载的库名（primary + fallback 合并）
     */
    public Set<String> getDatabases() {
        Set<String> dbs = new HashSet<>();
        dbs.addAll(primaryTables.keySet());
        dbs.addAll(fallbackTables.keySet());
        return dbs;
    }

    /**
     * 返回表首次匹配的库名
     */
    public String getTableDatabase(String tableName) {
        // 先查 primary env
        for (String db : primaryTables.keySet()) {
            if (primaryTables.get(db).containsKey(tableName)) return db;
        }
        // 再查 fallback env
        for (String db : fallbackTables.keySet()) {
            if (fallbackTables.get(db).containsKey(tableName)) return db;
        }
        return null;
    }

    // ------------------------------------------------------------------ Entity 映射

    public String resolveEntityToTable(String entityClassName) {
        return entityTableMap.getOrDefault(entityClassName,
                NameConverter.entityClassToTable(entityClassName));
    }

    // ------------------------------------------------------------------ 项目→主库映射

    /**
     * 根据 artifactId 查找对应的主库名
     *
     * @return 主库名，若映射不存在返回 null
     */
    public String resolveProjectDatabase(String artifactId) {
        return artifactId != null ? projectDatabaseMap.get(artifactId) : null;
    }

    /**
     * 根据 artifactId 查找并设置主库名
     * <p>若映射不存在，不修改 primaryDatabase。</p>
     *
     * @return true 表示成功设置，false 表示映射不存在
     */
    public boolean setPrimaryDatabaseByProject(String artifactId) {
        String db = resolveProjectDatabase(artifactId);
        if (db != null) {
            this.primaryDatabase = db;
            LOG.info("Primary database set to '{}' via project artifactId '{}'", db, artifactId);
            return true;
        }
        LOG.warn("No project→database mapping found for artifactId '{}', primaryDatabase unchanged", artifactId);
        return false;
    }

    public String getPrimaryDatabase() {
        return primaryDatabase;
    }

    public void setPrimaryDatabase(String primaryDatabase) {
        this.primaryDatabase = primaryDatabase;
    }

    // ------------------------------------------------------------------ 统计

    public int getPrimaryTableCount() {
        return countAll(primaryTables);
    }

    public int getFallbackTableCount() {
        return countAll(fallbackTables);
    }

    private int countAll(Map<String, Map<String, TableMetadata>> envData) {
        return envData.values().stream().mapToInt(Map::size).sum();
    }
}
