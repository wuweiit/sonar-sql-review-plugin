#!/usr/bin/env python3
"""
Schema 导出脚本（多库版）
从数据库 information_schema 导出表结构、索引、行数，
生成 SonarQube SQL Review 插件所需的 JSON 文件。
支持同时导出多个数据库，每个库的表存放在独立子目录中。

目录结构:
  output/
  ├── production/
  │   ├── app_production/          # 库1
  │   │   ├── app_user.json
  │   │   └── app_article.json
  │   └── order_production/        # 库2
  │       ├── order_info.json
  │       └── ...
  ├── dev/
  │   ├── app_dev/
  │   └── order_dev/
  └── entity_table_map.json

用法:
  # 方式1: 指定具体库名（逗号分隔）
  python export_schema.py \
    --host=prod-db.internal --port=3306 \
    --user=db_reader --password=xxx \
    --database=app_production,order_production \
    --env=production \
    --output=/opt/sonar-schemas

  # 方式2: 按库前缀自动发现（自动导出所有匹配的库）
  python export_schema.py \
    --host=prod-db.internal --port=3306 \
    --user=db_reader --password=xxx \
    --db-prefix=app_,order_ \
    --env=production \
    --output=/opt/sonar-schemas

  # 导出开发环境
python export_schema.py \
    --host=dev.jinliwangluo.com --port=3306 \
    --user=root --password=xxxx \
    --db-prefix=pzds_ \
    --env=dev \
    --output=./sonar-schemas

依赖:
  pip install pymysql
"""

import argparse
import json
import os
from datetime import datetime, timezone

import pymysql


def get_connection(host, port, user, password):
    return pymysql.connect(
        host=host, port=port,
        user=user, password=password,
        database="information_schema",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def fetch_databases_by_prefix(conn, prefixes):
    """按前缀自动发现数据库
    prefixes: 前缀列表，如 ['app_', 'order_']
    """
    databases = []
    with conn.cursor() as cur:
        for prefix in prefixes:
            cur.execute(
                "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
                "WHERE SCHEMA_NAME LIKE %s ORDER BY SCHEMA_NAME",
                (f"{prefix}%",),
            )
            for row in cur.fetchall():
                db_name = row["SCHEMA_NAME"]
                if db_name not in databases:
                    databases.append(db_name)
    return databases


def fetch_tables(conn, database):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT TABLE_NAME, TABLE_ROWS "
            "FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA = %s AND TABLE_TYPE = 'BASE TABLE' "
            "ORDER BY TABLE_NAME",
            (database,),
        )
        return cur.fetchall()


def fetch_columns(conn, database, table_name):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY "
            "FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s "
            "ORDER BY ORDINAL_POSITION",
            (database, table_name),
        )
        return cur.fetchall()


def fetch_indexes(conn, database, table_name):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, INDEX_TYPE "
            "FROM information_schema.STATISTICS "
            "WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s "
            "ORDER BY INDEX_NAME, SEQ_IN_INDEX",
            (database, table_name),
        )
        return cur.fetchall()


def build_table_metadata(table_row, columns, indexes, database, env):
    table_name = table_row["TABLE_NAME"]
    row_count = table_row["TABLE_ROWS"] or 0

    col_list = []
    for col in columns:
        col_list.append({
            "name": col["COLUMN_NAME"],
            "type": col["COLUMN_TYPE"],
            "nullable": col["IS_NULLABLE"] == "YES",
        })

    idx_map = {}
    for idx in indexes:
        name = idx["INDEX_NAME"]
        if name not in idx_map:
            idx_map[name] = {
                "name": name,
                "columns": [],
                "unique": idx["NON_UNIQUE"] == 0,
                "type": idx["INDEX_TYPE"],
            }
        idx_map[name]["columns"].append(idx["COLUMN_NAME"])

    return {
        "table": table_name,
        "database": database,
        "environment": env,
        "rowCount": row_count,
        "lastSyncTime": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "columns": col_list,
        "indexes": list(idx_map.values()),
    }


def scan_entity_table_map(project_root):
    entity_map = {}
    if not project_root or not os.path.isdir(project_root):
        return entity_map

    import re
    pattern = re.compile(r'@TableName\s*\(\s*"([^"]+)"\s*\)')

    for root, _, files in os.walk(project_root):
        for f in files:
            if not f.endswith(".java"):
                continue
            filepath = os.path.join(root, f)
            try:
                with open(filepath, "r", encoding="utf-8") as fh:
                    content = fh.read()
                match = pattern.search(content)
                if match:
                    class_name = f.replace(".java", "")
                    table_name = match.group(1)
                    entity_map[class_name] = table_name
            except Exception:
                continue

    return entity_map


def export(args):
    conn = get_connection(args.host, args.port, args.user, args.password)
    output_root = os.path.abspath(args.output)
    env_dir = os.path.join(output_root, args.env)
    os.makedirs(env_dir, exist_ok=True)

    # 确定要导出的库列表
    if args.db_prefix:
        # 按前缀自动发现
        prefixes = [p.strip() for p in args.db_prefix.split(",") if p.strip()]
        databases = fetch_databases_by_prefix(conn, prefixes)
        if not databases:
            print(f"No databases found matching prefix(es): {prefixes}")
            conn.close()
            return
        print(f"Auto-discovered {len(databases)} database(s) by prefix {prefixes}: "
              f"{databases}")
    elif args.database:
        # 手动指定库名
        databases = [db.strip() for db in args.database.split(",") if db.strip()]
        print(f"Exporting {len(databases)} database(s) to env '{args.env}': "
              f"{databases}")
    else:
        print("Error: must specify --database or --db-prefix")
        conn.close()
        return

    total_tables = 0
    for database in databases:
        db_dir = os.path.join(env_dir, database)
        os.makedirs(db_dir, exist_ok=True)

        tables = fetch_tables(conn, database)
        print(f"\n  [{database}] Found {len(tables)} tables")
        total_tables += len(tables)

        for table_row in tables:
            table_name = table_row["TABLE_NAME"]
            columns = fetch_columns(conn, database, table_name)
            indexes = fetch_indexes(conn, database, table_name)
            metadata = build_table_metadata(
                table_row, columns, indexes, database, args.env
            )

            file_path = os.path.join(db_dir, f"{table_name}.json")
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(metadata, f, ensure_ascii=False, indent=2)

            print(f"    {table_name}.json ({len(columns)} columns, "
                  f"{len(metadata['indexes'])} indexes, ~{metadata['rowCount']} rows)")

    # 扫描 Entity 注解（输出到根目录）
    if args.project_root:
        entity_map = scan_entity_table_map(args.project_root)
        if entity_map:
            map_path = os.path.join(output_root, "entity_table_map.json")
            with open(map_path, "w", encoding="utf-8") as f:
                json.dump(entity_map, f, ensure_ascii=False, indent=2)
            print(f"\nExported entity_table_map.json ({len(entity_map)} mappings)")

    conn.close()
    print(f"\nDone! {total_tables} tables from {len(databases)} database(s) "
          f"saved to: {env_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Export database schema for SonarQube SQL Review plugin")
    parser.add_argument("--host", required=True, help="Database host")
    parser.add_argument("--port", type=int, default=3306, help="Database port")
    parser.add_argument("--user", required=True, help="Database user")
    parser.add_argument("--password", required=True, help="Database password")
    parser.add_argument("--database", default=None,
                        help="Target database name(s), comma-separated "
                             "(e.g. app_production,order_production). "
                             "Optional if --db-prefix is specified")
    parser.add_argument("--db-prefix", default=None,
                        help="Auto-discover databases by prefix(es), comma-separated "
                             "(e.g. app_,order_). "
                             "Optional if --database is specified")
    parser.add_argument("--env", required=True, choices=["production", "dev"],
                        help="Environment label (production or dev)")
    parser.add_argument("--output", default="./schemas",
                        help="Output root directory (default: ./schemas)")
    parser.add_argument("--project-root", default=None,
                        help="Java project root (optional, for @TableName scan)")
    args = parser.parse_args()
    export(args)
