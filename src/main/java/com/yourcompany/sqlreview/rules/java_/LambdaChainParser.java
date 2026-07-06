package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.util.NameConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lambda 查询链解析器
 * <p>
 * 从 Java 源码行中识别 Lambda 查询链，提取：
 * <ul>
 *   <li>实体类名（从 {@code Wrappers.<Entity>lambdaQuery()} 或 {@code new MPJLambdaWrapper<>()} 等）</li>
 *   <li>条件列信息（从 {@code .eq(Entity::getXxx)} 等方法引用，含实体类归属）</li>
 *   <li>JOIN 信息（MPJ {@code .leftJoin(Entity.class, ...)}）</li>
 *   <li>select() 列信息</li>
 *   <li>链的终结状态（.list()/.page()/.last() 等）</li>
 * </ul>
 * </p>
 *
 * @author marker
 */
public class LambdaChainParser {

    /** 匹配 Lambda 查询入口，捕获实体类名 */
    private static final Pattern QUERY_START = Pattern.compile(
            "<\\s*([A-Z][a-zA-Z0-9]*)\\s*>\\s*lambdaQuery\\s*\\("
            + "|new\\s+LambdaQueryWrapper\\s*<\\s*([A-Z][a-zA-Z0-9]*)\\s*>"
            + "|LambdaQueryWrapper\\s*<\\s*([A-Z][a-zA-Z0-9]*)\\s*>"
            + "|new\\s+MPJLambdaWrapper\\s*<\\s*([A-Z][a-zA-Z0-9]*)\\s*>"
            + "|MPJLambdaWrapper\\s*<\\s*([A-Z][a-zA-Z0-9]*)\\s*>");

    /**
     * 条件方法：带 getter 方法引用
     * group(1) = method, group(2) = entityClass, group(3) = getterName, group(4) = valueExpr
     */
    private static final Pattern CONDITION_WITH_GETTER = Pattern.compile(
            "\\.((?:eq|ne|gt|ge|lt|le|like|likeLeft|likeRight|notLike|notLikeLeft|notLikeRight"
            + "|in|notIn|between|notBetween|isNull|isNotNull|orderByDesc|orderByAsc))"
            + "\\s*\\(\\s*([A-Z][a-zA-Z0-9]*)::([a-zA-Z0-9]+)\\s*(?:,\\s*(.+?))?\\s*\\)");

    /**
     * 条件方法：带 boolean 前置条件
     * group(1) = method, group(2) = entityClass, group(3) = getterName, group(4) = valueExpr
     */
    private static final Pattern CONDITION_WITH_BOOLEAN = Pattern.compile(
            "\\.((?:eq|ne|gt|ge|lt|le|like|likeLeft|likeRight|notLike|notLikeLeft|notLikeRight"
            + "|in|notIn|between|notBetween))"
            + "\\s*\\(\\s*[a-zA-Z][a-zA-Z0-9.()]*\\s*,\\s*([A-Z][a-zA-Z0-9]*)::([a-zA-Z0-9]+)\\s*(?:,\\s*(.+?))?\\s*\\)");

    /** 匹配 .last() 调用 */
    private static final Pattern LAST_CALL = Pattern.compile("\\.last\\s*\\(");

    /** 终结调用 */
    private static final Pattern TERMINATION = Pattern.compile(
            "\\.(?:list|one|count|page|getOne|getMap|getObj|getCount|update|delete|remove)\\s*\\(");

    /** selectAll() */
    private static final Pattern SELECT_ALL = Pattern.compile("\\.selectAll\\s*\\(");

    /** 语句结束 */
    private static final Pattern STATEMENT_END = Pattern.compile(
            ";|\\.(?:list|one|count|page|getOne|getMap|getObj|getCount|update|delete|remove)\\s*\\(");

    /** 匹配 .apply("SQL片段" ...) 调用，捕获第一个字符串参数 */
    private static final Pattern APPLY_CALL = Pattern.compile(
            "\\.apply\\s*\\(\\s*\"([^\"]+)\"");

    /**
     * 匹配 MPJ JOIN 方法：.leftJoin(Entity.class, ...) / .innerJoin(Entity.class, ...) / .rightJoin(Entity.class, ...)
     * group(1) = joinType (leftJoin/innerJoin/rightJoin), group(2) = entityClass
     */
    private static final Pattern JOIN_CALL = Pattern.compile(
            "\\.(leftJoin|innerJoin|rightJoin)\\s*\\(\\s*([A-Z][a-zA-Z0-9]*)\\.class\\b");

    /**
     * 匹配 select() 调用中的方法引用：Entity::getXxx
     * group(1) = entityClass, group(2) = getterName
     */
    private static final Pattern SELECT_METHOD_REF = Pattern.compile(
            "([A-Z][a-zA-Z0-9]*)::([a-zA-Z0-9]+)");

    /**
     * 匹配 .select(...) 调用（非 selectAll）
     */
    private static final Pattern SELECT_CALL = Pattern.compile(
            "\\.select\\s*\\(");

    /**
     * 解析 Java 源码中的所有 Lambda 查询链
     *
     * @param lines 源码行数组
     * @return 解析出的链列表
     */
    public List<LambdaChain> parse(String[] lines) {
        List<LambdaChain> chains = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            if (isComment(line)) {
                i++;
                continue;
            }

            Matcher startMatcher = QUERY_START.matcher(line);
            if (startMatcher.find()) {
                // 实体类名可能在多个 group 中
                String entityClass = getFirstNonNull(startMatcher, 1, 2, 3, 4, 5);
                int startLine = i + 1;

                // 收集整个链
                StringBuilder chain = new StringBuilder(line);
                int j = i + 1;
                while (j < lines.length && !STATEMENT_END.matcher(chain).find()) {
                    chain.append("\n").append(lines[j]);
                    j++;
                }

                String chainText = chain.toString();
                LambdaChain lc = buildChain(entityClass, startLine, chainText, lines, i, j);
                chains.add(lc);
                i = j;
            } else {
                i++;
            }
        }
        return chains;
    }

    /**
     * 根据收集到的链文本构建 LambdaChain 对象
     */
    private LambdaChain buildChain(String entityClass, int startLine, String chainText,
                                   String[] lines, int startIdx, int endIdx) {
        LambdaChain lc = new LambdaChain();
        lc.setEntityClass(entityClass);
        lc.setStartLine(startLine);
        lc.setText(chainText);
        lc.setHasLast(LAST_CALL.matcher(chainText).find());
        lc.setHasTermination(TERMINATION.matcher(chainText).find());
        lc.setHasSelectAll(SELECT_ALL.matcher(chainText).find());

        // 逐行解析
        for (int k = startIdx; k < endIdx && k < lines.length; k++) {
            String row = lines[k];
            if (isComment(row)) continue;

            // 尝试匹配标准条件
            Matcher m = CONDITION_WITH_GETTER.matcher(row);
            if (m.find()) {
                addCondition(lc, m, k + 1);
                continue;
            }

            // 尝试匹配带 boolean 前缀的条件
            Matcher mb = CONDITION_WITH_BOOLEAN.matcher(row);
            if (mb.find()) {
                addCondition(lc, mb, k + 1);
            }

            // 尝试匹配 .apply() 调用
            Matcher ma = APPLY_CALL.matcher(row);
            if (ma.find()) {
                String sqlFragment = ma.group(1);
                lc.getApplyCalls().add(new LambdaChain.ApplyInfo(sqlFragment, k + 1));
            }

            // 尝试匹配 JOIN 调用
            Matcher mj = JOIN_CALL.matcher(row);
            if (mj.find()) {
                String joinMethod = mj.group(1);
                String joinEntity = mj.group(2);
                String joinType = joinMethod.replace("Join", "").toUpperCase(); // LEFT/INNER/RIGHT
                lc.getJoins().add(new LambdaChain.JoinInfo(joinType, joinEntity, k + 1));
            }

            // 尝试匹配 select() 调用（非 selectAll）
            if (SELECT_CALL.matcher(row).find() && !SELECT_ALL.matcher(row).find()) {
                parseSelectColumns(lc, row);
            }
        }
        return lc;
    }

    /**
     * 解析 select() 调用中的列信息
     */
    private void parseSelectColumns(LambdaChain lc, String row) {
        // 提取 select(...) 括号内的内容
        int start = row.indexOf("select(");
        if (start < 0) return;
        // 找到对应的闭合括号（简化：找最后一个 )）
        int end = row.lastIndexOf(')');
        if (end <= start) return;
        String selectContent = row.substring(start + 7, end);

        // 从 select 内容中提取所有 Entity::getter 方法引用
        Matcher refMatcher = SELECT_METHOD_REF.matcher(selectContent);
        while (refMatcher.find()) {
            String entity = refMatcher.group(1);
            String getter = refMatcher.group(2);
            String colName = NameConverter.methodRefToColumn(getter);
            lc.getSelectColumns().add(new LambdaChain.SelectColInfo(entity, getter, colName));
        }
    }

    private void addCondition(LambdaChain lc, Matcher m, int lineNum) {
        String method = m.group(1);
        String condEntityClass = m.group(2);
        String getterName = m.group(3);
        String valueExpr = m.groupCount() >= 4 && m.group(4) != null ? m.group(4).trim() : "";
        String columnName = NameConverter.methodRefToColumn(getterName);
        lc.getConditions().add(
                new LambdaChain.ConditionInfo(method, condEntityClass, getterName, columnName, valueExpr, lineNum));
    }

    private String getFirstNonNull(Matcher m, int... groups) {
        for (int g : groups) {
            if (m.group(g) != null) return m.group(g);
        }
        return null;
    }

    private boolean isComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
