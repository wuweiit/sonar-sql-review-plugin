# SonarQube SQL Review Plugin
![sonar-sql-review-banner.png](docs%2Fimage%2Fsonar-sql-review-banner.png)


一个用于检查 MyBatis XML Mapper SQL 语句质量的 SonarQube 插件。通过分析 SQL 语法并结合生产数据库 Schema 元数据，在代码审查阶段发现潜在的性能问题和安全隐患。


作者：marker 博客：http://www.wuweibi.com


## 功能特性

### 规则检查

| 规则ID | 名称 | 严重级别 | 类型 | 依赖Schema |
|--------|------|----------|------|------------|
| SQL-001 | WHERE 条件字段缺少索引 | CRITICAL | BUG | ✅ |
| SQL-002 | 大表全表扫描 | CRITICAL | BUG | ✅ |
| SQL-003 | 使用 SELECT * | MAJOR | CODE_SMELL | ❌ |
| SQL-004 | 索引列使用函数导致索引失效 | CRITICAL | BUG | ✅ |
| SQL-101 | 大表查询无 LIMIT | MAJOR | BUG | ✅ |
| SQL-103 | LIKE 以 % 开头 | MAJOR | CODE_SMELL | ❌ |
| SQL-201 | 动态 SQL 拼接风险 | MAJOR | VULNERABILITY | ❌ |

### 核心能力

- **MyBatis XML 解析**：自动解析 Mapper XML 文件中的 SQL 语句，支持动态标签（`<if>`、`<choose>`、`<foreach>` 等）
- **Schema 元数据驱动**：基于生产数据库的表结构、索引信息进行精准检查
- **多环境支持**：支持主环境 + 降级环境的 Schema 查找策略
- **Issue 定位**：将问题定位到对应的 Java Mapper 接口方法上
- **PR 扫描支持**：支持 Pull Request 模式，只报告新代码中的问题

## 技术栈

- **SonarQube Plugin API**: 10.14.0.2599
- **最低兼容版本**: 10.11.0.2468
- **Java**: 17
- **构建工具**: Maven

## 项目结构

```
sonar-sql-review-plugin/
├── src/main/java/com/yourcompany/sqlreview/
│   ├── SqlReviewPlugin.java          # 插件入口
│   ├── parser/                       # XML 解析器
│   │   ├── MyBatisXmlParser.java     # MyBatis XML 解析
│   │   └── SqlStatement.java         # SQL 语句模型
│   ├── rules/                        # 规则定义
│   │   ├── SqlRulesDefinition.java   # 规则注册
│   │   ├── SqlXmlRule.java           # 规则接口
│   │   ├── NoIndexWhereRule.java     # SQL-001
│   │   ├── FullTableScanRule.java    # SQL-002
│   │   ├── SelectStarXmlRule.java    # SQL-003
│   │   ├── IndexFunctionRule.java    # SQL-004
│   │   ├── NoLimitLargeTableRule.java# SQL-101
│   │   ├── LikeLeadingWildcardRule.java # SQL-103
│   │   └── DynamicConcatRule.java    # SQL-201
│   ├── schema/                       # Schema 管理
│   │   ├── SchemaRegistry.java       # Schema 注册表
│   │   ├── TableMetadata.java        # 表元数据
│   │   └── IndexMetadata.java        # 索引元数据
│   ├── sensor/
│   │   └── MyBatisXmlSensor.java     # Sensor 实现
│   ├── settings/
│   │   └── SqlReviewProperties.java  # 配置项定义
│   └── util/
│       └── NameConverter.java        # 命名转换工具
└── pom.xml
```

## 安装部署

### 1. 构建插件

```bash
cd sonar-sql-review-plugin
mvn clean package
```

构建产物位于 `target/sonar-sql-review-plugin-1.0.0.jar`

### 2. 安装到 SonarQube

将 JAR 文件复制到 SonarQube 插件目录：

```bash
cp target/sonar-sql-review-plugin-1.0.0.jar $SONARQUBE_HOME/extensions/plugins/
```

重启 SonarQube 服务。

### 3. 配置 Schema 目录

在 SonarQube 管理界面 **Configuration > General Settings > SQL Review** 中配置：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| Schema Root Directory | Schema 根目录 | `/opt/sonar-schemas` |
| Primary Environment | 主环境名称 | `production` |
| Fallback Environment | 降级环境名称 | `dev` |
| Large Table Threshold | 大表行数阈值 | `10000` |
| No Limit Threshold | 无 LIMIT 告警阈值 | `50000` |
| Max Joins | 最大 JOIN 表数 | `3` |

## Schema 目录结构

```
/opt/sonar-schemas/
├── production/           # 主环境
│   ├── sys_user.json
│   ├── sys_role.json
│   └── ...
├── dev/                  # 降级环境
│   ├── sys_user.json
│   └── ...
└── entity_table_map.json # Entity 到表的映射（可选）
```

### Schema JSON 格式

schema 需要放在sonar-scanner 执行的服务器或本地上，这个后面改造为定时拉群仓库配置
```json
{
  "table": "sys_user",
  "database": "platform_db",
  "environment": "production",
  "rowCount": 150000,
  "lastSyncTime": "2024-01-15 10:30:00",
  "columns": [
    {"name": "id", "type": "bigint", "nullable": false},
    {"name": "username", "type": "varchar(64)", "nullable": false},
    {"name": "email", "type": "varchar(128)", "nullable": true}
  ],
  "indexes": [
    {"name": "idx_username", "columns": ["username"], "unique": true},
    {"name": "idx_email", "columns": ["email"], "unique": false}
  ]
}
```

## 使用方式

### 全量扫描

```bash
sonar-scanner \
  -Dsonar.projectKey=my-project \
  -Dsonar.sources=. \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=xxx
```

### PR 扫描

```bash
sonar-scanner \
  -Dsonar.projectKey=my-project \
  -Dsonar.sources=. \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=xxx \
  -Dsonar.pullrequest.key=123 \
  -Dsonar.pullrequest.branch=feature/xxx \
  -Dsonar.pullrequest.base=main
```

## 工作原理

1. **XML 解析**：Sensor 扫描项目中的 MyBatis Mapper XML 文件
2. **SQL 提取**：解析 `<select>`、`<insert>`、`<update>`、`<delete>` 标签中的 SQL
3. **Namespace 匹配**：通过 XML 的 `namespace` 属性匹配对应的 Java Mapper 接口
4. **规则检查**：对每条 SQL 执行所有启用的规则检查
5. **Issue 上报**：将发现的问题挂载到对应的 Java 文件上

## 开发指南

### 添加新规则

1. 在 `SqlRulesDefinition.java` 中注册规则
2. 创建规则类实现 `SqlXmlRule` 接口
3. 在 `MyBatisXmlSensor.java` 中注册规则实例

```java
// 1. 注册规则定义
repository.createRule("SQL-XXX")
    .setName("规则名称")
    .setHtmlDescription("规则描述")
    .setSeverity(Severity.MAJOR)
    .setType(RuleType.CODE_SMELL)
    .setStatus(RuleStatus.READY);

// 2. 实现规则类
public class MyNewRule implements SqlXmlRule {
    @Override
    public List<Issue> check(SqlStatement stmt, SchemaRegistry schema) {
        // 检查逻辑
    }
}

// 3. 注册到 Sensor
rules.add(new MyNewRule());
```

## 许可证

MIT License
