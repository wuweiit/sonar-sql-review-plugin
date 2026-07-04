package com.yourcompany.sqlreview.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 表结构元数据模型
 *
 * @author marker
 */
public class TableMetadata {

    public static final TableMetadata EMPTY = new TableMetadata();

    private String table;
    private String database;
    private String environment;
    private long rowCount;
    private String lastSyncTime;
    private List<ColumnMetadata> columns = new ArrayList<>();
    private List<IndexMetadata> indexes = new ArrayList<>();

    public TableMetadata() {
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }

    public String getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(String lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetadata> columns) {
        this.columns = columns;
    }

    public List<IndexMetadata> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexMetadata> indexes) {
        this.indexes = indexes;
    }

    /**
     * 列元数据（内部类）
     */
    public static class ColumnMetadata {
        private String name;
        private String type;
        private boolean nullable;

        public ColumnMetadata() {
        }

        public ColumnMetadata(String name, String type, boolean nullable) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }
    }
}
