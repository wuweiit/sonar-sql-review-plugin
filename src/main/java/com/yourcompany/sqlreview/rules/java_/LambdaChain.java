package com.yourcompany.sqlreview.rules.java_;

import java.util.ArrayList;
import java.util.List;

/**
 * Lambda 查询链模型，表示一个完整的 MyBatis Plus Lambda Wrapper 链式调用
 *
 * @author marker
 */
public class LambdaChain {

    /** 实体类名（如 AppUserEntity） */
    private String entityClass;

    /** 链起始行号（1-based） */
    private int startLine;

    /** 链完整文本 */
    private String text;

    /** 条件列及对应信息 */
    private final List<ConditionInfo> conditions = new ArrayList<>();

    /** .apply() 调用信息 */
    private final List<ApplyInfo> applyCalls = new ArrayList<>();

    /** JOIN 信息（MPJ Lambda Wrapper） */
    private final List<JoinInfo> joins = new ArrayList<>();

    /** select() 指定的列（如果有） */
    private final List<SelectColInfo> selectColumns = new ArrayList<>();

    /** 是否有 .last() 调用 */
    private boolean hasLast;

    /** 是否有分页/终结调用（.page() / .list() 等） */
    private boolean hasTermination;

    /** 是否有 selectAll() */
    private boolean hasSelectAll;

    /** 解析出的数据库名（由 Sensor 设置，来自 @DS 注解或 project_database_map.json） */
    private String resolvedDatabase;

    public String getEntityClass() { return entityClass; }
    public void setEntityClass(String entityClass) { this.entityClass = entityClass; }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<ConditionInfo> getConditions() { return conditions; }

    public List<ApplyInfo> getApplyCalls() { return applyCalls; }

    public List<JoinInfo> getJoins() { return joins; }

    public List<SelectColInfo> getSelectColumns() { return selectColumns; }

    public boolean isHasLast() { return hasLast; }
    public void setHasLast(boolean hasLast) { this.hasLast = hasLast; }

    public boolean isHasTermination() { return hasTermination; }
    public void setHasTermination(boolean hasTermination) { this.hasTermination = hasTermination; }

    public boolean isHasSelectAll() { return hasSelectAll; }
    public void setHasSelectAll(boolean hasSelectAll) { this.hasSelectAll = hasSelectAll; }

    public String getResolvedDatabase() { return resolvedDatabase; }
    public void setResolvedDatabase(String resolvedDatabase) { this.resolvedDatabase = resolvedDatabase; }

    /**
     * 单个条件方法调用信息
     */
    public static class ConditionInfo {
        /** 方法名：eq, like, in, between 等 */
        private final String method;
        /** 条件所属实体类（如 AppArticleCategoryEntity），从方法引用中提取 */
        private final String entityClass;
        /** 条件列对应的 getter 方法名（如 getStatus） */
        private final String getterName;
        /** 转换后的列名（如 status） */
        private final String columnName;
        /** 条件值的字符串表示（用于 LIKE 检测） */
        private final String valueExpr;
        /** 条件所在行号（1-based） */
        private final int line;

        public ConditionInfo(String method, String entityClass, String getterName,
                             String columnName, String valueExpr, int line) {
            this.method = method;
            this.entityClass = entityClass;
            this.getterName = getterName;
            this.columnName = columnName;
            this.valueExpr = valueExpr;
            this.line = line;
        }

        public String getMethod() { return method; }
        public String getEntityClass() { return entityClass; }
        public String getGetterName() { return getterName; }
        public String getColumnName() { return columnName; }
        public String getValueExpr() { return valueExpr; }
        public int getLine() { return line; }
    }

    /**
     * .apply() 调用信息（原始 SQL 片段）
     */
    public static class ApplyInfo {
        /** apply() 中的 SQL 片段字符串（如 "YEAR(create_time) = {0}"） */
        private final String sqlFragment;
        /** 调用所在行号（1-based） */
        private final int line;

        public ApplyInfo(String sqlFragment, int line) {
            this.sqlFragment = sqlFragment;
            this.line = line;
        }

        public String getSqlFragment() { return sqlFragment; }
        public int getLine() { return line; }
    }

    /**
     * MPJ JOIN 信息
     */
    public static class JoinInfo {
        /** JOIN 类型：LEFT / INNER / RIGHT */
        private final String joinType;
        /** JOIN 目标实体类名 */
        private final String entityClass;
        /** JOIN 所在行号 */
        private final int line;

        public JoinInfo(String joinType, String entityClass, int line) {
            this.joinType = joinType;
            this.entityClass = entityClass;
            this.line = line;
        }

        public String getJoinType() { return joinType; }
        public String getEntityClass() { return entityClass; }
        public int getLine() { return line; }
    }

    /**
     * select() 列信息
     */
    public static class SelectColInfo {
        /** 列所属实体类 */
        private final String entityClass;
        /** getter 方法名 */
        private final String getterName;
        /** 转换后的列名 */
        private final String columnName;

        public SelectColInfo(String entityClass, String getterName, String columnName) {
            this.entityClass = entityClass;
            this.getterName = getterName;
            this.columnName = columnName;
        }

        public String getEntityClass() { return entityClass; }
        public String getGetterName() { return getterName; }
        public String getColumnName() { return columnName; }
    }
}
