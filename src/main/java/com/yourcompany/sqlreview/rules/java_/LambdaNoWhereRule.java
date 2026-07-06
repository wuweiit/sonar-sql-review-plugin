package com.yourcompany.sqlreview.rules.java_;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL-302: 检测 Lambda Wrapper 查询链中缺少 WHERE 条件
 * <p>
 * 识别 {@code lambdaQuery()} 或 {@code LambdaQueryWrapper} 构建的查询链，
 * 若链中不包含任何条件方法（eq/ne/gt/lt/like/in/between 等），则报告问题。
 * </p>
 *
 * @author marker
 */
public class LambdaNoWhereRule {

    /** 匹配 Lambda 查询入口 */
    private static final Pattern LAMBDA_QUERY_START = Pattern.compile(
            "lambdaQuery\\s*\\(|new\\s+LambdaQueryWrapper\\s*[<(]|LambdaQueryWrapper\\s*[<(]");

    /** 条件方法列表，链中包含任一即视为有 WHERE 条件 */
    private static final Pattern CONDITION_METHODS = Pattern.compile(
            "\\.(?:eq|ne|gt|ge|lt|le|like|likeLeft|likeRight|notLike|notLikeLeft|notLikeRight"
            + "|in|notIn|between|notBetween|isNull|isNotNull|exists|notExists"
            + "|apply|last)\\s*\\(");

    /** 语句结束标识：分号或 .list() / .one() / .count() / .page() / .getOne() 等终结调用 */
    private static final Pattern CHAIN_END = Pattern.compile(";|\\.(?:list|one|count|page|getOne|getMap|getObj|getCount)\\s*\\(");

    /**
     * 检查 Java 源码中 Lambda 查询是否缺少 WHERE 条件
     *
     * @param lines 源码行数组
     * @return 发现的问题列表
     */
    public List<LambdaSelectAllRule.JavaIssue> check(String[] lines) {
        List<LambdaSelectAllRule.JavaIssue> issues = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher startMatcher = LAMBDA_QUERY_START.matcher(line);
            if (startMatcher.find() && !isComment(line)) {
                int startLine = i + 1;
                StringBuilder chain = new StringBuilder(line);
                int j = i + 1;
                // 收集整个链式调用直到语句结束
                while (j < lines.length && !CHAIN_END.matcher(chain).find()) {
                    chain.append("\n").append(lines[j]);
                    j++;
                }
                String chainStr = chain.toString();
                // 检查链中是否有条件方法
                if (!CONDITION_METHODS.matcher(chainStr).find()) {
                    issues.add(new LambdaSelectAllRule.JavaIssue(
                            "SQL-302",
                            startLine,
                            "Lambda 查询缺少 WHERE 条件，可能导致全表扫描",
                            "添加 .eq()/.like() 等条件方法，或使用 .last(\"LIMIT N\") 限制返回行数"
                    ));
                }
                i = j;
            } else {
                i++;
            }
        }
        return issues;
    }

    private boolean isComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
