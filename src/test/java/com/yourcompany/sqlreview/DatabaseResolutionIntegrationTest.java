package com.yourcompany.sqlreview;

import com.yourcompany.sqlreview.rules.java_.LambdaChain;
import com.yourcompany.sqlreview.rules.java_.LambdaChainParser;
import com.yourcompany.sqlreview.rules.java_.LambdaNoIndexRule;
import com.yourcompany.sqlreview.rules.java_.LambdaNoLimitRule;
import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;
import com.yourcompany.sqlreview.schema.DataSourceResolver;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库定位优先级集成测试
 * <p>
 * 验证完整优先级链：@DS 注解 > project_database_map.json > 表名反推兜底
 *
 * @author marker
 */
class DatabaseResolutionIntegrationTest {

    private SchemaRegistry schema;
    private DataSourceResolver dsResolver;
    private LambdaChainParser parser;
    private LambdaNoLimitRule noLimitRule;

    @BeforeEach
    void setUp() {
        schema = new SchemaRegistry();
        Path rootDir = Paths.get("src/test/resources/schemas");
        schema.loadFromDirectory(rootDir, "production", "dev");

        dsResolver = new DataSourceResolver();
        parser = new LambdaChainParser();
        noLimitRule = new LambdaNoLimitRule();
    }

    // === 优先级 1: @DS 注解覆盖主库 ===

    @Test
    void priority_dsAnnotation_overridesProjectDb() {
        // artifactId 映射到 pzds_platform，但 @DS("shared_db") 覆盖
        schema.setPrimaryDatabaseByProject("platform-service");
        assertThat(schema.getPrimaryDatabase()).isEqualTo("pzds_platform");

        // 扫描带 @DS 的类
        dsResolver.scanFile(
                "package com.example;\n" +
                "import com.baomidou.dynamic.datasource.annotation.DS;\n" +
                "@DS(\"shared_db\")\n" +
                "public class OrderService {\n" +
                "    public void query() {}\n" +
                "}\n"
        );

        // 解析：@DS 优先
        Optional<String> ds = dsResolver.resolve("OrderService", "AppUserEntity");
        assertThat(ds).contains("shared_db");

        // 设置到 chain 并验证规则使用 shared_db 的元数据
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };
        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(ds.orElse(schema.getPrimaryDatabase()));

        // shared_db.app_user 只有 5000 行，不触发大表规则
        List<JavaIssue> issues = noLimitRule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    // === 优先级 2: project_database_map.json 映射 ===

    @Test
    void priority_projectMapping_whenNoDs() {
        // 无 @DS 注解，使用 artifactId 映射
        schema.setPrimaryDatabaseByProject("platform-service");
        assertThat(schema.getPrimaryDatabase()).isEqualTo("pzds_platform");

        // 无 @DS 的类
        dsResolver.scanFile(
                "package com.example;\n" +
                "public class OrderService {\n" +
                "    public void query() {}\n" +
                "}\n"
        );

        Optional<String> ds = dsResolver.resolve("OrderService", null);
        assertThat(ds).isEmpty();

        // 降级到 primaryDatabase
        String resolvedDb = ds.orElse(schema.getPrimaryDatabase());
        assertThat(resolvedDb).isEqualTo("pzds_platform");

        // pzds_platform.app_user 有 150000 行，触发大表规则
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };
        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(resolvedDb);

        List<JavaIssue> issues = noLimitRule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }

    // === 优先级 3: 表名反推兜底 ===

    @Test
    void priority_tableScan_whenNoMapping() {
        // 无 @DS + 无映射 + 无主库
        Optional<String> ds = dsResolver.resolve("UnknownService", null);
        assertThat(ds).isEmpty();
        assertThat(schema.getPrimaryDatabase()).isNull();

        // 遍历所有库查找表
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserName, \"test\")",
            "    .list();"
        };
        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(null); // 无数据库信息

        // null database 时使用遍历模式，app_production.app_user 会被找到
        LambdaNoIndexRule noIndexRule = new LambdaNoIndexRule();
        // user_name 在 app_production 中有 idx_user_name 索引
        List<JavaIssue> issues = noIndexRule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    // === 同名表不同库 + @DS ===

    @Test
    void priority_sameTableDifferentDb_withDs() {
        // app_user 在 app_production 和 shared_db 中都存在
        dsResolver.scanFile(
                "package com.example;\n" +
                "import com.baomidou.dynamic.datasource.annotation.DS;\n" +
                "@DS(\"shared_db\")\n" +
                "public class UserService {\n" +
                "    public void query() {}\n" +
                "}\n"
        );

        Optional<String> ds = dsResolver.resolve("UserService", null);
        assertThat(ds).contains("shared_db");

        // 验证使用 shared_db 的 app_user（5000 行，有 idx_name 索引）
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getUserName, \"test\")",
            "    .list();"
        };
        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase("shared_db");

        // shared_db.app_user 5000 行 < 10000 阈值，不触发大表规则
        List<JavaIssue> issues = noLimitRule.check(chains.get(0), schema);
        assertThat(issues).isEmpty();
    }

    // === 同名表不同库 + 项目映射 ===

    @Test
    void priority_sameTableDifferentDb_withProjectMapping() {
        schema.setPrimaryDatabaseByProject("platform-service");
        assertThat(schema.getPrimaryDatabase()).isEqualTo("pzds_platform");

        // 无 @DS
        Optional<String> ds = dsResolver.resolve("SomeService", null);
        assertThat(ds).isEmpty();

        // 使用主库 pzds_platform 的 app_user（150000 行）
        String[] lines = {
            "Wrappers.<AppUserEntity>lambdaQuery()",
            "    .eq(AppUserEntity::getStatus, 1)",
            "    .list();"
        };
        List<LambdaChain> chains = parser.parse(lines);
        chains.get(0).setResolvedDatabase(schema.getPrimaryDatabase());

        // pzds_platform.app_user 150000 行，触发大表规则
        List<JavaIssue> issues = noLimitRule.check(chains.get(0), schema);
        assertThat(issues).hasSize(1);
    }

    // === 完整端到端优先级链 ===

    @Test
    void endToEnd_fullPriorityChain() {
        // 1. 加载 Schema
        assertThat(schema.getDatabases()).contains("app_production", "order_production", "shared_db", "pzds_platform");

        // 2. 设置主库（通过 artifactId 映射）
        schema.setPrimaryDatabaseByProject("platform-service");

        // 3. 扫描 @DS 注解
        dsResolver.scanFile(
                "package com.example;\n" +
                "import com.baomidou.dynamic.datasource.annotation.DS;\n" +
                "@DS(\"order_production\")\n" +
                "public class OrderService {\n" +
                "    public void query() {}\n" +
                "}\n"
        );
        dsResolver.scanFile(
                "package com.example;\n" +
                "public class UserService {\n" +
                "    public void query() {}\n" +
                "}\n"
        );

        // 4. 验证 @DS 优先级高于主库映射
        Optional<String> orderDs = dsResolver.resolve("OrderService", null);
        assertThat(orderDs).contains("order_production");

        // 5. 验证无 @DS 时降级到主库
        Optional<String> userDs = dsResolver.resolve("UserService", null);
        assertThat(userDs).isEmpty();
        String userDb = userDs.orElse(schema.getPrimaryDatabase());
        assertThat(userDb).isEqualTo("pzds_platform");

        // 6. 验证数据库维度查找
        assertThat(schema.getTable("app_user", "pzds_platform")).isNotNull();
        assertThat(schema.getTable("app_user", "pzds_platform").getRowCount()).isEqualTo(150000);
        assertThat(schema.getTable("app_user", "shared_db")).isNotNull();
        assertThat(schema.getTable("app_user", "shared_db").getRowCount()).isEqualTo(5000);

        // 7. 验证 order_info 在 order_production 中
        assertThat(schema.getTable("order_info", "order_production")).isNotNull();
        assertThat(schema.getTable("order_info", "order_production").getDatabase()).isEqualTo("order_production");
    }

    // === 方法级 @DS 优先级 ===

    @Test
    void priority_methodLevelDs_overridesClassLevel() {
        dsResolver.scanFile(
                "package com.example;\n" +
                "import com.baomidou.dynamic.datasource.annotation.DS;\n" +
                "@DS(\"main\")\n" +
                "public class MixedService {\n" +
                "    @DS(\"order_production\")\n" +
                "    public void queryOrder() {}\n" +
                "    public void queryDefault() {}\n" +
                "}\n"
        );

        // 方法级 @DS 优先
        Optional<String> methodDs = dsResolver.resolve("MixedService", null, "queryOrder");
        assertThat(methodDs).contains("order_production");

        // 无方法级 @DS 时降级到类级
        Optional<String> classDs = dsResolver.resolve("MixedService", null, "queryDefault");
        assertThat(classDs).contains("main");
    }
}
