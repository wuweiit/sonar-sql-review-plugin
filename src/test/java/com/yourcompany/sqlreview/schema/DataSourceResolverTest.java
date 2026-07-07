package com.yourcompany.sqlreview.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataSourceResolver 单元测试
 *
 * @author marker
 */
class DataSourceResolverTest {

    private static final Path DS_DIR = Paths.get("src/test/resources/ds");

    private String readFile(String name) throws IOException {
        return Files.readString(DS_DIR.resolve(name));
    }

    // --- 扫描测试 ---

    @Test
    void scan_classLevelDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("ClassLevelDs.java"));
        assertThat(resolver.getClassLevelDs()).containsEntry("ClassLevelDs", "order");
    }

    @Test
    void scan_methodLevelDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MethodLevelDs.java"));
        assertThat(resolver.getMethodLevelDs())
                .containsEntry("MethodLevelDs.queryOrders", "read")
                .containsEntry("MethodLevelDs.saveOrder", "write");
    }

    @Test
    void scan_mapperWithDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MapperWithDsMapper.java"));
        assertThat(resolver.getClassLevelDs()).containsEntry("MapperWithDsMapper", "slave");
        assertThat(resolver.getMapperDs()).containsEntry("MapperWithDsMapper", "slave");
    }

    @Test
    void scan_noDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("NoDs.java"));
        assertThat(resolver.getClassLevelDs()).isEmpty();
        assertThat(resolver.getMethodLevelDs()).isEmpty();
    }

    @Test
    void scan_mixedDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MixedDs.java"));
        assertThat(resolver.getClassLevelDs()).containsEntry("MixedDs", "main");
        assertThat(resolver.getMethodLevelDs()).containsEntry("MixedDs.queryOrder", "order");
    }

    @Test
    void scan_emptyContent() {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile("");
        assertThat(resolver.getClassLevelDs()).isEmpty();
    }

    @Test
    void scan_nullContent() {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(null);
        assertThat(resolver.getClassLevelDs()).isEmpty();
    }

    @Test
    void scan_multipleMethods() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MethodLevelDs.java"));
        // 应有 2 个方法级 @DS
        assertThat(resolver.getMethodLevelDs()).hasSize(2);
    }

    // --- resolve 测试 ---

    @Test
    void resolve_classLevelOnly() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("ClassLevelDs.java"));
        Optional<String> ds = resolver.resolve("ClassLevelDs", null);
        assertThat(ds).contains("order");
    }

    @Test
    void resolve_methodOverridesClass() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MixedDs.java"));
        // 方法级 @DS 优先
        Optional<String> ds = resolver.resolve("MixedDs", null, "queryOrder");
        assertThat(ds).contains("order");
    }

    @Test
    void resolve_classLevel_whenMethodHasNoDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MixedDs.java"));
        // queryDefault 没有方法级 @DS，降级到类级
        Optional<String> ds = resolver.resolve("MixedDs", null, "queryDefault");
        assertThat(ds).contains("main");
    }

    @Test
    void resolve_mapperLevelDs() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MapperWithDsMapper.java"));
        // 当调用类无 @DS 时，检查 Mapper
        // MapperWithDsEntity → MapperWithDsMapper (去 Entity 加 Mapper)
        Optional<String> ds = resolver.resolve("SomeService", "MapperWithDsEntity");
        assertThat(ds).contains("slave");
    }

    @Test
    void resolve_noDsAnnotation() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("NoDs.java"));
        Optional<String> ds = resolver.resolve("NoDs", null);
        assertThat(ds).isEmpty();
    }

    @Test
    void resolve_callerPriority() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        // Caller has @DS("order"), Mapper has @DS("slave")
        resolver.scanFile(readFile("ClassLevelDs.java"));
        resolver.scanFile(readFile("MapperWithDsMapper.java"));
        // Caller 的 @DS 优先于 Mapper
        Optional<String> ds = resolver.resolve("ClassLevelDs", "AppUserEntity");
        assertThat(ds).contains("order");
    }

    @Test
    void resolve_methodLevelHighestPriority() throws IOException {
        DataSourceResolver resolver = new DataSourceResolver();
        resolver.scanFile(readFile("MixedDs.java"));
        // 方法级 > 类级
        Optional<String> dsMethod = resolver.resolve("MixedDs", null, "queryOrder");
        Optional<String> dsClass = resolver.resolve("MixedDs", null, "queryDefault");
        assertThat(dsMethod).contains("order");
        assertThat(dsClass).contains("main");
    }

    @Test
    void resolve_unknownClass_returnsEmpty() {
        DataSourceResolver resolver = new DataSourceResolver();
        Optional<String> ds = resolver.resolve("UnknownClass", "UnknownEntity");
        assertThat(ds).isEmpty();
    }

    @Test
    void resolve_nullClassName_returnsEmpty() {
        DataSourceResolver resolver = new DataSourceResolver();
        Optional<String> ds = resolver.resolve(null, null);
        assertThat(ds).isEmpty();
    }
}
