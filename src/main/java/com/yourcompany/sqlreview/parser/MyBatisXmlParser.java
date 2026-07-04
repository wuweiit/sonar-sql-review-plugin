package com.yourcompany.sqlreview.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis XML Mapper 文件解析器
 * 从 XML 中提取 SQL 语句，处理动态标签
 *
 * @author marker
 */
public class MyBatisXmlParser {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisXmlParser.class);

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern WHERE_COL_PATTERN = Pattern.compile(
            "(?i)WHERE\\s+.*?([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|!=|<>|>|>=|<|<=|LIKE|IN|BETWEEN|IS)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\b");
    private static final Pattern SELECT_STAR_PATTERN = Pattern.compile("(?i)SELECT\\s+\\*");
    private static final Pattern DOLLAR_PATTERN = Pattern.compile("\\$\\{");
    private static final Pattern HASH_PATTERN = Pattern.compile("#\\{[^}]+\\}");

    private MyBatisXmlParser() {
    }

    public static List<SqlStatement> parse(InputFile xmlFile) {
        try {
            String content = xmlFile.contents();
            return parseContent(content, xmlFile.filename());
        } catch (Exception e) {
            LOG.error("Failed to parse XML file: {}", xmlFile.filename(), e);
            return List.of();
        }
    }

    public static List<SqlStatement> parseFile(Path xmlFile) {
        try {
            String content = new String(Files.readAllBytes(xmlFile), StandardCharsets.UTF_8);
            return parseContent(content, xmlFile.toString());
        } catch (IOException e) {
            LOG.error("Failed to read XML file: {}", xmlFile, e);
            return List.of();
        }
    }

    public static List<SqlStatement> parseContent(String xmlContent, String filename) {
        List<SqlStatement> statements = new ArrayList<>();
        try {
            // 移除 DOCTYPE 声明（避免外部 DTD 加载）
            String cleaned = xmlContent.replaceAll("<!DOCTYPE[^>]*>", "");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(cleaned.getBytes(StandardCharsets.UTF_8)));

            NodeList mapperNodes = doc.getElementsByTagName("mapper");
            if (mapperNodes.getLength() == 0) return statements;

            Element mapper = (Element) mapperNodes.item(0);
            String namespace = mapper.getAttribute("namespace");

            // 解析 select/update/delete/insert 标签
            parseStatements(mapper, "select", "SELECT", statements, filename, namespace, cleaned);
            parseStatements(mapper, "update", "UPDATE", statements, filename, namespace, cleaned);
            parseStatements(mapper, "delete", "DELETE", statements, filename, namespace, cleaned);
            parseStatements(mapper, "insert", "INSERT", statements, filename, namespace, cleaned);

        } catch (Exception e) {
            LOG.error("Failed to parse XML content: {}", filename, e);
        }
        return statements;
    }

    private static void parseStatements(Element mapper, String tagName, String sqlType,
                                         List<SqlStatement> statements, String filename, String namespace, String xmlContent) {
        NodeList nodes = mapper.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("id");

            // 提取 SQL 文本（含动态标签处理）
            String rawSql = extractSqlText(element);
            String normalizedSql = normalizeSql(rawSql);

            SqlStatement stmt = new SqlStatement();
            stmt.setId(id);
            stmt.setType(sqlType);
            stmt.setSql(normalizedSql);
            stmt.setRawXml(rawSql);
            stmt.setFilePath(filename);
            stmt.setNamespace(namespace);
            stmt.setLineNumber(getLineNumber(element, xmlContent));

            // 提取表名
            stmt.setTables(extractTables(normalizedSql));

            // 提取 WHERE 条件列
            stmt.setConditionColumns(extractConditionColumns(normalizedSql));

            // 检测 SELECT *
            stmt.setSelectStar(SELECT_STAR_PATTERN.matcher(normalizedSql).find());

            // 检测 LIMIT
            stmt.setHasLimit(LIMIT_PATTERN.matcher(normalizedSql).find());

            // 检测 ${} 动态拼接
            stmt.setHasDynamicConcat(DOLLAR_PATTERN.matcher(rawSql).find());

            // 生成动态标签变体
            stmt.setVariants(generateVariants(element, sqlType));

            statements.add(stmt);
        }
    }

    /**
     * 获取元素在 XML 文件中的行号
     * 通过正则匹配 id 属性来定位行号
     */
    private static int getLineNumber(Element element, String xmlContent) {
        String id = element.getAttribute("id");
        String tagName = element.getTagName();
        // 匹配 <select id="xxx" 或 <insert id="xxx" 等
        Pattern pattern = Pattern.compile("<" + tagName + "[^>]*\\bid\\s*=\\s*[\"']" + Pattern.quote(id) + "[\"']");
        Matcher matcher = pattern.matcher(xmlContent);
        if (matcher.find()) {
            // 计算匹配位置之前的换行符数量
            String before = xmlContent.substring(0, matcher.start());
            int lineNum = 1;
            for (int i = 0; i < before.length(); i++) {
                if (before.charAt(i) == '\n') {
                    lineNum++;
                }
            }
            return lineNum;
        }
        return 1;
    }

    /**
     * 递归提取元素中的 SQL 文本
     */
    private static String extractSqlText(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                if ("if".equals(name) || "choose".equals(name) || "when".equals(name)
                        || "otherwise".equals(name) || "where".equals(name)
                        || "set".equals(name) || "trim".equals(name)
                        || "foreach".equals(name)) {
                    sb.append(" ").append(extractSqlText(child)).append(" ");
                } else if ("include".equals(name)) {
                    sb.append(" /* include */ ");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 标准化 SQL（替换 #{} 为 ?，压缩空白）
     */
    private static String normalizeSql(String rawSql) {
        String normalized = HASH_PATTERN.matcher(rawSql).replaceAll("?");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * 从 SQL 中提取表名
     */
    static List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase();
            if (!tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    /**
     * 提取 WHERE 条件中的列名
     */
    static List<String> extractConditionColumns(String sql) {
        List<String> columns = new ArrayList<>();
        // 简单模式：WHERE col op value
        Matcher matcher = WHERE_COL_PATTERN.matcher(sql);
        while (matcher.find()) {
            String col = matcher.group(1).toLowerCase();
            if (!columns.contains(col) && !isSqlKeyword(col)) {
                columns.add(col);
            }
        }
        // 补充：AND/OR 后面的条件列
        Pattern andOrPattern = Pattern.compile(
                "(?i)(?:AND|OR)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|!=|<>|>|>=|<|<=|LIKE|IN|BETWEEN|IS)");
        Matcher andOrMatcher = andOrPattern.matcher(sql);
        while (andOrMatcher.find()) {
            String col = andOrMatcher.group(1).toLowerCase();
            if (!columns.contains(col) && !isSqlKeyword(col)) {
                columns.add(col);
            }
        }
        return columns;
    }

    private static boolean isSqlKeyword(String word) {
        return List.of("where", "and", "or", "not", "null", "select", "from", "set").contains(word);
    }

    /**
     * 生成动态标签变体（简化：所有 if 为 true 的完整路径 + 所有 if 为 false 的基础路径）
     */
    private static List<SqlStatement> generateVariants(Element element, String sqlType) {
        // 简化实现：生成 2 个变体
        // 1. 基础 SQL（所有 if 条件为 false）
        // 2. 完整 SQL（所有 if 条件为 true）- 已作为主 SQL
        List<SqlStatement> variants = new ArrayList<>();

        // 基础变体（不含 if 内容）
        StringBuilder baseSql = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                baseSql.append(child.getTextContent());
            }
            // 跳过 if/choose 等动态标签
        }
        String base = normalizeSql(baseSql.toString());
        if (!base.isEmpty()) {
            SqlStatement baseVariant = new SqlStatement();
            baseVariant.setType(sqlType);
            baseVariant.setSql(base);
            baseVariant.setConditionColumns(extractConditionColumns(base));
            variants.add(baseVariant);
        }

        return variants;
    }
}
