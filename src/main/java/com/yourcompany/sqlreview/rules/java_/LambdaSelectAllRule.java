package com.yourcompany.sqlreview.rules.java_;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL-301: 检测 Lambda Wrapper 中 selectAll() 调用（等价于 SELECT *）
 * <p>
 * 匹配模式：{@code .selectAll(EntityClass.class)} 或 {@code .selectAll()}
 * </p>
 *
 * @author marker
 */
public class LambdaSelectAllRule {

    /**
     * 匹配 .selectAll( 调用，前面不能有字母（排除方法名包含 selectAll 的情况）
     */
    private static final Pattern SELECT_ALL_PATTERN =
            Pattern.compile("(?<![a-zA-Z])\\.selectAll\\s*\\(");

    /**
     * 检查 Java 源码中的 selectAll() 调用
     *
     * @param lines 源码行数组
     * @return 发现的问题列表，每个元素包含行号（1-based）
     */
    public List<JavaIssue> check(String[] lines) {
        List<JavaIssue> issues = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 跳过注释行
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            Matcher matcher = SELECT_ALL_PATTERN.matcher(line);
            if (matcher.find()) {
                issues.add(new JavaIssue(
                        "SQL-301",
                        i + 1,
                        "selectAll() 等价于 SELECT *，请使用 .select() 指定需要的字段",
                        "将 .selectAll() 替换为 .select(Entity::getField1, Entity::getField2, ...)"
                ));
            }
        }
        return issues;
    }

    /**
     * Java 审查问题
     */
    public static class JavaIssue {
        private final String ruleId;
        private final int line;
        private final String message;
        private final String suggestion;

        public JavaIssue(String ruleId, int line, String message, String suggestion) {
            this.ruleId = ruleId;
            this.line = line;
            this.message = message;
            this.suggestion = suggestion;
        }

        public String getRuleId() { return ruleId; }
        public int getLine() { return line; }
        public String getMessage() { return message; }
        public String getSuggestion() { return suggestion; }
    }
}
