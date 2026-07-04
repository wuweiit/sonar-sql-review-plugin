// Test sample: Good Lambda usage patterns for SQL Review
// This file is used by LambdaSelectAllRuleTest - should NOT trigger issues
package com.example;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

public class GoodLambdaUsage {

    // Explicit select with specific columns
    public void explicitSelectExample() {
        Wrappers.<AppUserEntity>lambdaQuery()
            .select(AppUserEntity::getUserId, AppUserEntity::getUserName)
            .eq(AppUserEntity::getUserId, 1L);
    }

    // Proper condition on query
    public void withConditionExample() {
        Wrappers.<AppUserEntity>lambdaQuery()
            .select(AppUserEntity::getUserId, AppUserEntity::getUserName, AppUserEntity::getEmail)
            .eq(AppUserEntity::getUserName, "test")
            .list();
    }
}
