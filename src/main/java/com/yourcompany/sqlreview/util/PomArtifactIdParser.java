package com.yourcompany.sqlreview.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从项目 pom.xml 中提取项目自身的 artifactId（非 parent 块中的 artifactId）
 *
 * @author marker
 */
public final class PomArtifactIdParser {

    private static final Logger LOG = LoggerFactory.getLogger(PomArtifactIdParser.class);

    /**
     * 匹配 <parent>...</parent> 块（含标签本身，用于剔除）
     */
    private static final Pattern PARENT_BLOCK = Pattern.compile(
            "<parent>[\\s\\S]*?</parent>", Pattern.CASE_INSENSITIVE);

    /**
     * 匹配 <artifactId>value</artifactId>
     */
    private static final Pattern ARTIFACT_ID = Pattern.compile(
            "<artifactId\\s*>([^<]+)</artifactId>", Pattern.CASE_INSENSITIVE);

    private PomArtifactIdParser() {
    }

    /**
     * 从 pom.xml 中解析项目自身的 artifactId
     *
     * @param pomPath pom.xml 文件路径，可为 null
     * @return 项目 artifactId，若无法解析返回 empty
     */
    public static Optional<String> parseArtifactId(Path pomPath) {
        if (pomPath == null) {
            return Optional.empty();
        }
        if (!Files.exists(pomPath)) {
            LOG.debug("pom.xml not found: {}", pomPath);
            return Optional.empty();
        }

        try {
            String content = Files.readString(pomPath);
            return parseFromContent(content);
        } catch (IOException e) {
            LOG.error("Failed to read pom.xml: {}", pomPath, e);
            return Optional.empty();
        }
    }

    /**
     * 从 pom.xml 内容中解析项目自身的 artifactId（内部可见，供测试使用）
     */
    static Optional<String> parseFromContent(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        // 先移除 <parent>...</parent> 块，避免误取父 artifactId
        String withoutParent = PARENT_BLOCK.matcher(content).replaceAll("");

        Matcher m = ARTIFACT_ID.matcher(withoutParent);
        if (m.find()) {
            String artifactId = m.group(1).trim();
            if (!artifactId.isEmpty()) {
                return Optional.of(artifactId);
            }
        }
        return Optional.empty();
    }
}
