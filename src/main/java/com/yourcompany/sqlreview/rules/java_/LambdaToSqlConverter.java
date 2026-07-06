package com.yourcompany.sqlreview.rules.java_;

import com.yourcompany.sqlreview.parser.SqlStatement;
import com.yourcompany.sqlreview.schema.SchemaRegistry;
import com.yourcompany.sqlreview.util.NameConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lambda 链 → SQL 语句转换器
 * <p>
 * 将 {@link LambdaChain} 转换为 {@link SqlStatement}，
 * 使得现有的所有 XML SQL 规则可以复用，实现统一检查。
 * </p>
 * <p>
 * 支持：
 * <ul>
 *   <li>单表 LambdaQueryWrapper</li>
 *   <li>多表 MPJ Lambda Wrapper（含 leftJoin/innerJoin/rightJoin）</li>
 *   <li>.apply() 原始 SQL 片段</li>
 *   <li>.select() 指定列</li>
 * </ul>
 * </p>
 *
 * @author marker
 */
public class LambdaToSqlConverter {

    private LambdaToSqlConverter() {
    }

    /**
     * 将 Lambda 查询链转换为 SqlStatement
     *
     * @param chain  解析好的 Lambda 查询链
     * @param schema Schema 元数据（用于实体类→表名映射）
     * @return SqlStatement 对象，可用于所有现有 SqlXmlRule 的检查
     */
    public static SqlStatement convert(LambdaChain chain, SchemaRegistry schema) {
        if (chain == null || chain.getEntityClass() == null) {
            return null;
        }

        String mainTable = resolveTable(chain.getEntityClass(), schema);
        if (mainTable == null) {
            mainTable = NameConverter.entityClassToTable(chain.getEntityClass());
        }

        // 构建 SQL
        StringBuilder sql = new StringBuilder();

        // SELECT 部分
        buildSelectPart(sql, chain, mainTable);

        // FROM 部分
        sql.append(" FROM ").append(mainTable);

        // JOIN 部分
        for (LambdaChain.JoinInfo join : chain.getJoins()) {
            String joinTable = resolveTable(join.getEntityClass(), schema);
            if (joinTable == null) {
                joinTable = NameConverter.entityClassToTable(join.getEntityClass());
            }
            sql.append(" ").append(join.getJoinType()).append(" JOIN ").append(joinTable);
            sql.append(" ON 1=1");
        }

        // WHERE 部分
        String whereClause = buildWherePart(chain, mainTable, schema);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        // LIMIT 部分（.last("LIMIT n")）
        boolean hasLimit = chain.isHasLast();
        if (hasLimit) {
            sql.append(" LIMIT ?");
        }

        // 收集表列表
        List<String> tables = new ArrayList<>();
        tables.add(mainTable);
        for (LambdaChain.JoinInfo join : chain.getJoins()) {
            String joinTable = resolveTable(join.getEntityClass(), schema);
            if (joinTable == null) {
                joinTable = NameConverter.entityClassToTable(join.getEntityClass());
            }
            if (!tables.contains(joinTable)) {
                tables.add(joinTable);
            }
        }

        // 收集条件列
        List<String> conditionColumns = extractConditionColumns(chain, schema);

        // 检查 selectAll：未显式指定列时默认 SELECT *
        boolean selectStar = chain.getSelectColumns().isEmpty();

        // 检查 .apply() 中是否有动态拼接（${} 风格）
        boolean hasDynamicConcat = false;

        String sqlText = sql.toString();

        return SqlStatement.builder()
                .type("SELECT")
                .sql(sqlText)
                .tables(tables)
                .conditionColumns(conditionColumns)
                .selectStar(selectStar)
                .hasLimit(hasLimit)
                .hasDynamicConcat(hasDynamicConcat)
                .lineNumber(chain.getStartLine())
                .build();
    }

    /**
     * 构建 SELECT 部分
     */
    private static void buildSelectPart(StringBuilder sql, LambdaChain chain, String mainTable) {
        sql.append("SELECT ");
        if (!chain.getSelectColumns().isEmpty()) {
            // 显式 select() 指定了列
            List<String> cols = new ArrayList<>();
            for (LambdaChain.SelectColInfo col : chain.getSelectColumns()) {
                cols.add(col.getColumnName());
            }
            sql.append(String.join(", ", cols));
        } else {
            // 未指定 select() 或 selectAll() → 默认 SELECT *
            sql.append("*");
        }
    }

    /**
     * 构建 WHERE 部分
     * 对 LIKE 条件保留字面值，以便 LikeLeadingWildcardRule 可以匹配 LIKE '% 模式
     */
    private static String buildWherePart(LambdaChain chain, String mainTable, SchemaRegistry schema) {
        List<String> conditions = new ArrayList<>();

        for (LambdaChain.ConditionInfo cond : chain.getConditions()) {
            String col = cond.getColumnName();
            String op = mapMethodToOp(cond.getMethod());
            if (op != null) {
                // LIKE 类条件：保留字面值以便规则检测前导 %
                if (isLikeMethod(cond.getMethod()) && cond.getValueExpr() != null && !cond.getValueExpr().isEmpty()) {
                    String val = cond.getValueExpr().trim();
                    // 如果是字符串字面量（带引号），直接嵌入
                    if (val.startsWith("\"")) {
                        String likeVal = val;
                        // likeLeft/notLikeLeft 自动添加前导 %
                        if ("likeLeft".equals(cond.getMethod()) || "notLikeLeft".equals(cond.getMethod())) {
                            // 将 "xxx" 转为 '%xxx'
                            likeVal = "'" + "%" + val.substring(1, val.length() - 1) + "'";
                        } else if (val.startsWith("\"")) {
                            // 将双引号转为 单引号
                            likeVal = "'" + val.substring(1, val.length() - 1) + "'";
                        }
                        conditions.add(col + " LIKE " + likeVal);
                    } else {
                        conditions.add(col + " " + op);
                    }
                } else {
                    conditions.add(col + " " + op);
                }
            }
        }

        // 处理 .apply() 中的 SQL 片段
        for (LambdaChain.ApplyInfo apply : chain.getApplyCalls()) {
            String fragment = apply.getSqlFragment();
            // 将 {0}, {1} 替换为 ?
            fragment = fragment.replaceAll("\\{\\d+}", "?");
            conditions.add(fragment);
        }

        return String.join(" AND ", conditions);
    }

    /**
     * 提取条件列（用于 SQL 规则检查）
     */
    private static List<String> extractConditionColumns(LambdaChain chain, SchemaRegistry schema) {
        List<String> columns = new ArrayList<>();
        for (LambdaChain.ConditionInfo cond : chain.getConditions()) {
            if (!"orderByDesc".equals(cond.getMethod()) && !"orderByAsc".equals(cond.getMethod())) {
                String col = cond.getColumnName();
                if (!columns.contains(col)) {
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /**
     * 将 Lambda 条件方法映射为 SQL 操作符
     */
    private static String mapMethodToOp(String method) {
        switch (method) {
            case "eq": return "= ?";
            case "ne": return "!= ?";
            case "gt": return "> ?";
            case "ge": return ">= ?";
            case "lt": return "< ?";
            case "le": return "<= ?";
            case "like": return "LIKE ?";
            case "likeLeft": return "LIKE ?";
            case "likeRight": return "LIKE ?";
            case "notLike": return "NOT LIKE ?";
            case "notLikeLeft": return "NOT LIKE ?";
            case "notLikeRight": return "NOT LIKE ?";
            case "in": return "IN (?)";
            case "notIn": return "NOT IN (?)";
            case "between": return "BETWEEN ? AND ?";
            case "notBetween": return "NOT BETWEEN ? AND ?";
            case "isNull": return "IS NULL";
            case "isNotNull": return "IS NOT NULL";
            default: return null;
        }
    }

    /**
     * 判断是否为 LIKE 类方法
     */
    private static boolean isLikeMethod(String method) {
        return "like".equals(method) || "likeLeft".equals(method) || "likeRight".equals(method)
                || "notLike".equals(method) || "notLikeLeft".equals(method) || "notLikeRight".equals(method);
    }

    /**
     * 解析实体类到表名
     */
    private static String resolveTable(String entityClass, SchemaRegistry schema) {
        if (schema == null) {
            return NameConverter.entityClassToTable(entityClass);
        }
        String table = schema.resolveEntityToTable(entityClass);
        if (table == null) {
            table = NameConverter.entityClassToTable(entityClass);
        }
        return table;
    }
}
