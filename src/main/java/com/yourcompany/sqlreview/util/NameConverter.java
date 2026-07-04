package com.yourcompany.sqlreview.util;

/**
 * 命名转换工具类
 *
 * @author marker
 */
public final class NameConverter {

    private NameConverter() {
    }

    /**
     * 驼峰转下划线
     * userName -> user_name
     * createUserId -> create_user_id
     */
    public static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 方法引用转列名
     * getUserName -> user_name
     * getId -> id
     */
    public static String methodRefToColumn(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return methodName;
        }
        String field = methodName.startsWith("get") ? methodName.substring(3) : methodName;
        if (field.isEmpty()) {
            return field;
        }
        field = Character.toLowerCase(field.charAt(0)) + field.substring(1);
        return camelToSnake(field);
    }

    /**
     * Entity 类名转表名
     * AppUserEntity -> app_user
     */
    public static String entityClassToTable(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        String name = className.endsWith("Entity")
                ? className.substring(0, className.length() - 6)
                : className;
        return camelToSnake(name);
    }
}
