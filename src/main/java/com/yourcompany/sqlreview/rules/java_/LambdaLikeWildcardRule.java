package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.rules.java_.LambdaSelectAllRule.JavaIssue;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-304: Lambda LIKE 以 % 开头
 * <p>
 * 检测 Lambda 查询链中 .like() 方法的第二个参数是否以 "%" 开头，
 * 或使用了 "%xxx" / "%" + variable 等前缀通配符模式。
 * </p>
 *
 * @author marker
 */
public class LambdaLikeWildcardRule {

    /**
     * 检查 Lambda 查询链中 LIKE 是否存在前导通配符
     *
     * @param chain 解析好的 Lambda 查询链
     * @return 发现的问题列表
     */
    public List<JavaIssue> check(LambdaChain chain) {
        List<JavaIssue> issues = new ArrayList<>();

        for (LambdaChain.ConditionInfo cond : chain.getConditions()) {
            if (!isLikeMethod(cond.getMethod())) {
                continue;
            }
            String method = cond.getMethod();
            String val = cond.getValueExpr();

            // likeLeft/notLikeLeft 方法本身就表示左模糊，始终生成 LIKE '%xxx'
            boolean isLeftLike = "likeLeft".equals(method) || "notLikeLeft".equals(method);
            boolean hasWildcard = (val != null && !val.isEmpty()) && hasLeadingWildcard(val);

            if (isLeftLike || hasWildcard) {
                issues.add(new JavaIssue(
                        "SQL-304",
                        cond.getLine(),
                        String.format("Lambda LIKE 条件字段 '%s' 使用了前导通配符 '%%...'，无法使用索引",
                                cond.getColumnName()),
                        "避免在 LIKE 值的开头使用 '%'，改用右模糊 'xxx%'"
                ));
            }
        }
        return issues;
    }

    private boolean isLikeMethod(String method) {
        return "like".equals(method) || "likeLeft".equals(method) || "notLike".equals(method)
                || "notLikeLeft".equals(method);
    }

    /**
     * 判断值表达式是否包含前导通配符
     * 匹配："%xxx"、"%" + var、"%" prefix、"%".concat(xxx)
     */
    private boolean hasLeadingWildcard(String valueExpr) {
        String v = valueExpr.trim();
        // "%xxx" 字符串字面量
        if (v.startsWith("\"%") || v.startsWith("'%")) {
            return true;
        }
        // "%" + variable 拼接
        if (v.startsWith("\"%\"") || v.startsWith("'%")) {
            return true;
        }
        // "%" . concat
        if (v.contains("\"%\"") && v.contains("+")) {
            int idx = v.indexOf("\"%\"");
            // "%" 在最前面
            if (idx == 0) {
                return true;
            }
        }
        return false;
    }
}
