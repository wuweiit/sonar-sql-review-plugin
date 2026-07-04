package com.yourcompany.sqlreview.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NameConverter 单元测试
 *
 * @author marker
 */
class NameConverterTest {

    @Test
    void camelToSnake_basicCases() {
        assertThat(NameConverter.camelToSnake("userName")).isEqualTo("user_name");
        assertThat(NameConverter.camelToSnake("createUserId")).isEqualTo("create_user_id");
        assertThat(NameConverter.camelToSnake("id")).isEqualTo("id");
        assertThat(NameConverter.camelToSnake("userId")).isEqualTo("user_id");
    }

    @Test
    void camelToSnake_edgeCases() {
        assertThat(NameConverter.camelToSnake("")).isEmpty();
        assertThat(NameConverter.camelToSnake(null)).isNull();
        assertThat(NameConverter.camelToSnake("a")).isEqualTo("a");
        assertThat(NameConverter.camelToSnake("ABC")).isEqualTo("a_b_c");
    }

    @Test
    void camelToSnake_multipleUpperCase() {
        assertThat(NameConverter.camelToSnake("appNewFeature")).isEqualTo("app_new_feature");
        assertThat(NameConverter.camelToSnake("appUserRole")).isEqualTo("app_user_role");
    }

    @Test
    void methodRefToColumn_basicCases() {
        assertThat(NameConverter.methodRefToColumn("getUserName")).isEqualTo("user_name");
        assertThat(NameConverter.methodRefToColumn("getId")).isEqualTo("id");
        assertThat(NameConverter.methodRefToColumn("getCreateUserId")).isEqualTo("create_user_id");
    }

    @Test
    void methodRefToColumn_edgeCases() {
        assertThat(NameConverter.methodRefToColumn("")).isEmpty();
        assertThat(NameConverter.methodRefToColumn(null)).isNull();
        assertThat(NameConverter.methodRefToColumn("get")).isEmpty();
        // Without "get" prefix
        assertThat(NameConverter.methodRefToColumn("userName")).isEqualTo("user_name");
    }

    @Test
    void entityClassToTable_basicCases() {
        assertThat(NameConverter.entityClassToTable("AppUserEntity")).isEqualTo("app_user");
        assertThat(NameConverter.entityClassToTable("AppArticleEntity")).isEqualTo("app_article");
        assertThat(NameConverter.entityClassToTable("AppNewFeatureEntity")).isEqualTo("app_new_feature");
    }

    @Test
    void entityClassToTable_noEntitySuffix() {
        assertThat(NameConverter.entityClassToTable("AppUser")).isEqualTo("app_user");
        assertThat(NameConverter.entityClassToTable("AppRole")).isEqualTo("app_role");
    }

    @Test
    void entityClassToTable_edgeCases() {
        assertThat(NameConverter.entityClassToTable("")).isEmpty();
        assertThat(NameConverter.entityClassToTable(null)).isNull();
    }
}
