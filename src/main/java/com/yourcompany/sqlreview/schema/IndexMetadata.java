package com.yourcompany.sqlreview.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 索引元数据模型
 *
 * @author marker
 */
public class IndexMetadata {
    private String name;
    private List<String> columns = new ArrayList<>();
    private boolean unique;
    private String type;

    public IndexMetadata() {
    }

    public IndexMetadata(String name, List<String> columns, boolean unique, String type) {
        this.name = name;
        this.columns = columns;
        this.unique = unique;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
