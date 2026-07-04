package com.yourcompany.sqlreview.schema;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试用 Schema 加载辅助类
 *
 * @author marker
 */
public final class TestSchemaHelper {

    private TestSchemaHelper() {
    }

    /**
     * 加载测试 Schema（production + dev）
     */
    public static SchemaRegistry loadTestSchema() {
        SchemaRegistry registry = new SchemaRegistry();
        Path rootDir = Paths.get("src/test/resources/schemas");
        registry.loadFromDirectory(rootDir, "production", "dev");
        return registry;
    }
}
