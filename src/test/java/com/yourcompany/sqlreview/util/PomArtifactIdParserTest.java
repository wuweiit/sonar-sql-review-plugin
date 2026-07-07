package com.yourcompany.sqlreview.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PomArtifactIdParser 单元测试
 *
 * @author marker
 */
class PomArtifactIdParserTest {

    private static final Path POM_DIR = Paths.get("src/test/resources/pom");

    @Test
    void parse_standardPom_returnsArtifactId() {
        Optional<String> result = PomArtifactIdParser.parseArtifactId(
                POM_DIR.resolve("pom-with-artifact.xml"));
        assertThat(result).contains("platform-service-cloud");
    }

    @Test
    void parse_pomWithParent_returnsProjectArtifactId() {
        Optional<String> result = PomArtifactIdParser.parseArtifactId(
                POM_DIR.resolve("pom-parent-artifact.xml"));
        assertThat(result).contains("my-service");
    }

    @Test
    void parse_missingArtifactId_returnsEmpty() {
        Optional<String> result = PomArtifactIdParser.parseArtifactId(
                POM_DIR.resolve("pom-without-artifact.xml"));
        assertThat(result).isEmpty();
    }

    @Test
    void parse_nullPath_returnsEmpty() {
        assertThat(PomArtifactIdParser.parseArtifactId(null)).isEmpty();
    }

    @Test
    void parse_fileNotFound_returnsEmpty() {
        assertThat(PomArtifactIdParser.parseArtifactId(
                Paths.get("nonexistent/pom.xml"))).isEmpty();
    }

    @Test
    void parseFromContent_standardContent() {
        String content = "<project><artifactId>my-app</artifactId></project>";
        assertThat(PomArtifactIdParser.parseFromContent(content)).contains("my-app");
    }

    @Test
    void parseFromContent_withParent() {
        String content = "<project><parent><artifactId>parent-app</artifactId></parent>"
                + "<artifactId>child-app</artifactId></project>";
        assertThat(PomArtifactIdParser.parseFromContent(content)).contains("child-app");
    }

    @Test
    void parseFromContent_nullContent_returnsEmpty() {
        assertThat(PomArtifactIdParser.parseFromContent(null)).isEmpty();
    }

    @Test
    void parseFromContent_emptyContent_returnsEmpty() {
        assertThat(PomArtifactIdParser.parseFromContent("")).isEmpty();
    }

    @Test
    void parseFromContent_blankContent_returnsEmpty() {
        assertThat(PomArtifactIdParser.parseFromContent("   ")).isEmpty();
    }
}
