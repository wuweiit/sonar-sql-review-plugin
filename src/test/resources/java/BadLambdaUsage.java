// Test sample: Bad Lambda usage patterns for SQL Review
// This file is used by LambdaSelectAllRuleTest - should trigger issues
package com.example;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

public class BadLambdaUsage {

    // SQL-003: selectAll() equivalent to SELECT *
    public void selectAllExample() {
        Wrappers.<AppUserEntity>lambdaQuery()
            .selectAll(AppUserEntity.class)  // Noncompliant {{selectAll() 等价于 SELECT *，请使用 .select() 指定需要的字段}}
            .eq(AppUserEntity::getStatus, 1);
    }

    // No condition on query
    public void noConditionExample() {
        Wrappers.<AppUserEntity>lambdaQuery()
            .select(AppUserEntity::getUserId, AppUserEntity::getUserName)
            .list();  // Noncompliant - no WHERE condition
    }
}

// Stub entity class for compilation
class AppUserEntity {
    private Long userId;
    private String userName;
    private String email;
    private Integer status;

    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getEmail() { return email; }
    public Integer getStatus() { return status; }
}
