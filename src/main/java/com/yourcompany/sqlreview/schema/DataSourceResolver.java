package com.yourcompany.sqlreview.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据源解析器：扫描 Java 文件中的 @DS 注解，按就近原则确定数据源
 * <p>
 * 支持的注解形式：
 * <ul>
 *   <li>{@code @DS("value")} — 字符串值</li>
 *   <li>{@code @DS(value = "value")} — 显式 value 属性</li>
 *   <li>{@code @DS(CONSTANT)} — 常量引用（提取标识符名）</li>
 * </ul>
 *
 * <p>优先级：方法级 @DS &gt; 类级 @DS &gt; Mapper 级 @DS</p>
 *
 * @author marker
 */
public class DataSourceResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceResolver.class);

    /**
     * 匹配 @DS(...) 注解，捕获值
     * group(1) = 字符串值（带引号），group(2) = 常量标识符
     */
    private static final Pattern DS_ANNOTATION = Pattern.compile(
            "@DS\\s*\\(\\s*(?:value\\s*=\\s*)?(?:\"([^\"]+)\"|([A-Z_][A-Z0-9_]*))\\s*\\)");

    /**
     * 匹配类/接口声明（含 public/abstract/final 修饰符）
     */
    private static final Pattern CLASS_DECL = Pattern.compile(
            "(?:(?:public|protected|private|abstract|final|static)\\s+)*"
                    + "(?:class|interface)\\s+([A-Za-z][A-Za-z0-9]*)");

    /**
     * 匹配方法声明（简化：访问修饰符 + 任意内容 + 方法名 + 括号）
     */
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(?:public|protected|private)\\s+"
                    + ".*?\\b([a-zA-Z][a-zA-Z0-9]*)\\s*\\(");

    /** 类简单名 → DS值（类级别注解） */
    private final Map<String, String> classLevelDs = new HashMap<>();

    /** "类名.方法名" → DS值（方法级别注解） */
    private final Map<String, String> methodLevelDs = new HashMap<>();

    /** Mapper 简单名 → DS值（Mapper 接口注解） */
    private final Map<String, String> mapperDs = new HashMap<>();

    /**
     * 扫描单个 Java 文件内容，提取 @DS 注解信息
     *
     * @param content Java 源码内容
     */
    public void scanFile(String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        String[] lines = content.split("\n", -1);
        String pendingDs = null;  // 最近遇到的 @DS 值（尚未绑定到类或方法）
        String currentClass = null;
        boolean isMapper = false; // 当前类是否为 Mapper 接口

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 跳过注释行
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                continue;
            }

            // 检测 @DS 注解
            Matcher dsMatcher = DS_ANNOTATION.matcher(line);
            if (dsMatcher.find()) {
                pendingDs = dsMatcher.group(1) != null ? dsMatcher.group(1) : dsMatcher.group(2);
                continue;
            }

            // 检测类/接口声明（始终跟踪当前类，无论是否有 @DS）
            Matcher classMatcher = CLASS_DECL.matcher(line);
            if (classMatcher.find()) {
                currentClass = classMatcher.group(1);
                isMapper = line.contains("interface");
                if (pendingDs != null) {
                    if (isMapper) {
                        mapperDs.put(currentClass, pendingDs);
                    }
                    classLevelDs.put(currentClass, pendingDs);
                    LOG.debug("@DS class-level: {} → {}", currentClass, pendingDs);
                    pendingDs = null;
                }
                continue;
            }

            // 检测方法声明（排除构造函数）
            if (currentClass != null && pendingDs != null) {
                Matcher methodMatcher = METHOD_DECL.matcher(line);
                if (methodMatcher.find()) {
                    String methodName = methodMatcher.group(1);
                    // 跳过构造函数（方法名与类名相同）
                    if (!methodName.equals(currentClass)) {
                        String key = currentClass + "." + methodName;
                        methodLevelDs.put(key, pendingDs);
                        LOG.debug("@DS method-level: {} \u2192 {}", key, pendingDs);
                        pendingDs = null;
                        continue;
                    }
                    // 构造函数行不消费 pendingDs
                    continue;
                }
            }

            // 其他行消耗掉 pendingDs（@DS 后面跟的不是类也不是方法，丢弃）
            if (!line.isEmpty() && !line.startsWith("@") && !line.startsWith("import")) {
                pendingDs = null;
            }
        }
    }

    /**
     * 解析给定类/实体对应的数据源
     * <p>
     * 优先级（就近原则）：
     * <ol>
     *   <li>当前类的方法级 @DS</li>
     *   <li>当前类的类级 @DS</li>
     *   <li>实体关联 Mapper 的类级 @DS</li>
     * </ol>
     *
     * @param currentClassName 当前扫描的类简单名（如 OrderService）
     * @param entityClass      Lambda 链中的实体类名（如 OrderInfoEntity），可为 null
     * @return 数据源名，若无 @DS 注解返回 empty
     */
    public Optional<String> resolve(String currentClassName, String entityClass) {
        // 1. 当前类的类级 @DS（无方法名时直接跳过方法级）
        if (currentClassName != null) {
            String classDs = classLevelDs.get(currentClassName);
            if (classDs != null) {
                return Optional.of(classDs);
            }
        }

        // 3. 实体类关联 Mapper 的 @DS
        if (entityClass != null) {
            // 实体类名 → 推断 Mapper 名：去掉 Entity 后缀 + "Mapper"
            String mapperName = inferMapperName(entityClass);
            String mapperDsValue = mapperDs.get(mapperName);
            if (mapperDsValue != null) {
                return Optional.of(mapperDsValue);
            }
            // 也查 classLevelDs（Mapper 接口也被扫描为类）
            String mapperClassDs = classLevelDs.get(mapperName);
            if (mapperClassDs != null) {
                return Optional.of(mapperClassDs);
            }
        }

        return Optional.empty();
    }

    /**
     * 解析给定类指定方法的数据源
     */
    public Optional<String> resolve(String currentClassName, String entityClass, String methodName) {
        // 方法级优先
        if (currentClassName != null && methodName != null) {
            String key = currentClassName + "." + methodName;
            String methodDs = methodLevelDs.get(key);
            if (methodDs != null) {
                return Optional.of(methodDs);
            }
        }
        // 降级到类级 / Mapper 级
        return resolve(currentClassName, entityClass);
    }

    /**
     * 从实体类名推断 Mapper 名：AppUserEntity → AppUserMapper
     */
    private String inferMapperName(String entityClass) {
        String base = entityClass;
        if (base.endsWith("Entity")) {
            base = base.substring(0, base.length() - "Entity".length());
        }
        return base + "Mapper";
    }

    // ------------------------------------------------------------------ 访问器（供测试使用）

    public Map<String, String> getClassLevelDs() {
        return classLevelDs;
    }

    public Map<String, String> getMethodLevelDs() {
        return methodLevelDs;
    }

    public Map<String, String> getMapperDs() {
        return mapperDs;
    }
}
