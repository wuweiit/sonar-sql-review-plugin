package com.yourcompany.sqlreview.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 语句模型（MyBatis XML 解析结果）
 *
 * @author marker
 */
public class SqlStatement {

    private String id;
    private String type;        // SELECT / INSERT / UPDATE / DELETE
    private String sql;
    private List<String> tables = new ArrayList<>();
    private List<String> conditionColumns = new ArrayList<>();
    private boolean selectStar;
    private boolean hasLimit;
    private boolean hasDynamicConcat;
    private String rawXml;
    private String filePath;
    private String namespace;  // mapper namespace (对应 Java 接口全限定名)
    private int lineNumber;
    private List<SqlStatement> variants = new ArrayList<>();

    public SqlStatement() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public List<String> getConditionColumns() {
        return conditionColumns;
    }

    public void setConditionColumns(List<String> conditionColumns) {
        this.conditionColumns = conditionColumns;
    }

    public boolean isSelectStar() {
        return selectStar;
    }

    public void setSelectStar(boolean selectStar) {
        this.selectStar = selectStar;
    }

    public boolean isHasLimit() {
        return hasLimit;
    }

    public void setHasLimit(boolean hasLimit) {
        this.hasLimit = hasLimit;
    }

    public boolean hasDynamicConcat() {
        return hasDynamicConcat;
    }

    public void setHasDynamicConcat(boolean hasDynamicConcat) {
        this.hasDynamicConcat = hasDynamicConcat;
    }

    public String getRawXml() {
        return rawXml;
    }

    public void setRawXml(String rawXml) {
        this.rawXml = rawXml;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public List<SqlStatement> getVariants() {
        return variants;
    }

    public void setVariants(List<SqlStatement> variants) {
        this.variants = variants;
    }

    /**
     * Builder 模式
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SqlStatement stmt = new SqlStatement();

        public Builder id(String id) { stmt.setId(id); return this; }
        public Builder type(String type) { stmt.setType(type); return this; }
        public Builder sql(String sql) { stmt.setSql(sql); return this; }
        public Builder tables(List<String> tables) { stmt.setTables(tables); return this; }
        public Builder conditionColumns(List<String> cols) { stmt.setConditionColumns(cols); return this; }
        public Builder selectStar(boolean v) { stmt.setSelectStar(v); return this; }
        public Builder hasLimit(boolean v) { stmt.setHasLimit(v); return this; }
        public Builder hasDynamicConcat(boolean v) { stmt.setHasDynamicConcat(v); return this; }
        public Builder rawXml(String v) { stmt.setRawXml(v); return this; }
        public Builder filePath(String v) { stmt.setFilePath(v); return this; }
        public Builder lineNumber(int v) { stmt.setLineNumber(v); return this; }

        public SqlStatement build() {
            return stmt;
        }
    }
}
