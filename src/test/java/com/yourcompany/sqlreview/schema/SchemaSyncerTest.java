package com.yourcompany.sqlreview.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaSyncer 单元测试
 *
 * @author marker
 */
class SchemaSyncerTest {

    @TempDir
    Path tempDir;

    @Test
    void parseDirectoryListing_shouldExtractLinks() {
        String html = """
                <!DOCTYPE html>
                <html><head><title>Index of /sonar-schema/</title></head>
                <body>
                <h1>Index of /sonar-schema/</h1>
                <hr>
                <pre>
                <a href="../">../</a>
                <a href="dev/">dev/</a>                                               06-Jul-2026 16:44       -
                <a href="production/">production/</a>                                   06-Jul-2026 16:44       -
                <a href="project_database_map.json">project_database_map.json</a>       06-Jul-2026 16:19    1214
                <a href="entity_table_map.json">entity_table_map.json</a>               06-Jul-2026 16:19     512
                </pre>
                </body></html>
                """;

        List<SchemaSyncer.DirectoryEntry> entries = SchemaSyncer.parseDirectoryListing(html);

        assertThat(entries).hasSize(4);
        assertThat(entries.get(0).name).isEqualTo("dev");
        assertThat(entries.get(0).isDirectory).isTrue();
        assertThat(entries.get(1).name).isEqualTo("production");
        assertThat(entries.get(1).isDirectory).isTrue();
        assertThat(entries.get(2).name).isEqualTo("project_database_map.json");
        assertThat(entries.get(2).isDirectory).isFalse();
        assertThat(entries.get(3).name).isEqualTo("entity_table_map.json");
        assertThat(entries.get(3).isDirectory).isFalse();
    }

    @Test
    void parseDirectoryListing_shouldSkipParentDirectory() {
        String html = """
                <a href="../">../</a>
                <a href="dev/">dev/</a>
                <a href="file.json">file.json</a>
                """;

        List<SchemaSyncer.DirectoryEntry> entries = SchemaSyncer.parseDirectoryListing(html);

        // "../" should be filtered out
        assertThat(entries).hasSize(2);
        assertThat(entries).noneMatch(e -> e.name.equals(".."));
    }

    @Test
    void parseDirectoryListing_shouldSkipQueryAndAbsoluteLinks() {
        String html = """
                <a href="?sort=name">Sort</a>
                <a href="#top">Top</a>
                <a href="http://external.com/file.json">External</a>
                <a href="/absolute/path/">Absolute</a>
                <a href="valid.json">valid.json</a>
                """;

        List<SchemaSyncer.DirectoryEntry> entries = SchemaSyncer.parseDirectoryListing(html);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).name).isEqualTo("valid.json");
    }

    @Test
    void parseDirectoryListing_shouldHandleEmptyHtml() {
        List<SchemaSyncer.DirectoryEntry> entries = SchemaSyncer.parseDirectoryListing("");
        assertThat(entries).isEmpty();
    }

    @Test
    void md5Hex_shouldReturnConsistentHash() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash1 = SchemaSyncer.md5Hex(data);
        String hash2 = SchemaSyncer.md5Hex(data);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(32); // MD5 hex is always 32 chars
        assertThat(hash1).isEqualTo("5eb63bbbe01eeed093cb22bb8f5acdc3");
    }

    @Test
    void md5Hex_differentContentDifferentHash() {
        byte[] data1 = "content A".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "content B".getBytes(StandardCharsets.UTF_8);

        assertThat(SchemaSyncer.md5Hex(data1)).isNotEqualTo(SchemaSyncer.md5Hex(data2));
    }

    @Test
    void constructor_shouldAppendTrailingSlash() {
        SchemaSyncer syncer = new SchemaSyncer("http://example.com/schemas", tempDir);
        assertThat(syncer.getBaseUrl()).isEqualTo("http://example.com/schemas/");
    }

    @Test
    void constructor_shouldKeepTrailingSlash() {
        SchemaSyncer syncer = new SchemaSyncer("http://example.com/schemas/", tempDir);
        assertThat(syncer.getBaseUrl()).isEqualTo("http://example.com/schemas/");
    }

    @Test
    void sync_shouldCreateLocalDirectory() {
        Path nonExistent = tempDir.resolve("sub/deep/schema");
        SchemaSyncer syncer = new SchemaSyncer("http://nonexistent-server-xyz.local/schema/", nonExistent);

        // sync will create local dir first, then fail on remote and throw IOException
        try {
            syncer.sync();
        } catch (IOException ignored) {
            // expected: server unreachable
        }

        assertThat(Files.isDirectory(nonExistent)).isTrue();
    }

    @Test
    void sync_shouldThrowWhenServerUnreachable() {
        SchemaSyncer syncer = new SchemaSyncer("http://nonexistent-server-xyz.local/schema/", tempDir);

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, syncer::sync);
    }

    @Test
    void parseDirectoryListing_shouldParseRealApacheOutput() {
        // Real Apache directory listing format from the actual server
        String html = """
                <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
                <html>
                <head><title>Index of /sonar-schema/dev/pzds_platform/</title></head>
                <body>
                <h1>Index of /sonar-schema/dev/pzds_platform/</h1>
                <hr>
                <pre>
                <a href="../">../</a>
                <a href="sys_user.json">sys_user.json</a>                                  06-Jul-2026 16:44    2795
                <a href="sys_role.json">sys_role.json</a>                                  06-Jul-2026 16:44    1532
                <a href="sys_menu.json">sys_menu.json</a>                                  06-Jul-2026 16:44    1821
                </pre>
                <hr>
                </body>
                </html>
                """;

        List<SchemaSyncer.DirectoryEntry> entries = SchemaSyncer.parseDirectoryListing(html);

        assertThat(entries).hasSize(3);
        assertThat(entries).allMatch(e -> !e.isDirectory);
        assertThat(entries).extracting(e -> e.name)
                .containsExactly("sys_user.json", "sys_role.json", "sys_menu.json");
    }

    @Test
    void getLocalRoot_shouldReturnConfiguredPath() {
        SchemaSyncer syncer = new SchemaSyncer("http://example.com/", tempDir);
        assertThat(syncer.getLocalRoot()).isEqualTo(tempDir);
    }
}
