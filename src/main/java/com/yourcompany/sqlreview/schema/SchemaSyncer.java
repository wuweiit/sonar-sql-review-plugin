package com.yourcompany.sqlreview.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema 元数据 HTTP 同步器
 * <p>
 * 从远程 HTTP 服务器（Apache/Nginx 目录列表）一次性同步 Schema 元数据到本地目录。
 * 每次 SonarQube 扫描时调用一次 {@link #sync()}，不支持也不需要定时同步。
 * </p>
 *
 * @author marker
 */
public class SchemaSyncer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaSyncer.class);

    /** 匹配 Apache/Nginx 目录列表中的链接 */
    private static final Pattern LINK_PATTERN =
            Pattern.compile("<a\\s+href=\"([^\"]+)\"[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);

    /** HTTP 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT = 10_000;

    /** HTTP 读取超时（毫秒） */
    private static final int READ_TIMEOUT = 30_000;

    private final String baseUrl;
    private final Path localRoot;

    /** 同步统计 */
    private int syncedFiles;
    private int skippedFiles;
    private int failedFiles;

    /**
     * @param baseUrl   远程 HTTP 基础 URL（如 http://dev.jinliwangluo.com/sonar-schema/）
     * @param localRoot 本地 Schema 根目录
     */
    public SchemaSyncer(String baseUrl, Path localRoot) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.localRoot = localRoot;
    }

    /**
     * 执行一次完整同步：递归遍历远程目录，将所有 .json 文件同步到本地。
     * <p>
     * 增量更新：基于 MD5 比对，内容未变的文件跳过写入。
     * 同步结束后如果有文件失败，抛出 IOException 让调用方感知。
     * </p>
     *
     * @throws IOException 本地目录创建失败、远程根目录不可达、或有文件同步失败时抛出
     */
    public void sync() throws IOException {
        syncedFiles = 0;
        skippedFiles = 0;
        failedFiles = 0;

        LOG.info("Starting schema sync from {} to {}", baseUrl, localRoot);
        Files.createDirectories(localRoot);

        // 根目录必须可达，否则直接抛异常
        List<DirectoryEntry> rootEntries = listRemoteDirectory(baseUrl);
        if (rootEntries == null) {
            throw new IOException("Remote schema server unreachable: " + baseUrl);
        }

        syncEntries(rootEntries, baseUrl, localRoot, 0);

        LOG.info("Schema sync complete: {} synced, {} skipped (unchanged), {} failed",
                syncedFiles, skippedFiles, failedFiles);

        if (failedFiles > 0) {
            throw new IOException("Schema sync completed with " + failedFiles + " failed file(s)");
        }
    }

    /**
     * 递归同步目录条目
     *
     * @param entries   当前目录已解析的条目列表
     * @param remoteUrl 当前远程目录 URL
     * @param localDir  当前本地目录
     * @param depth     递归深度（防止无限递归）
     */
    private void syncEntries(List<DirectoryEntry> entries, String remoteUrl, Path localDir, int depth) {
        if (depth > 5) {
            LOG.warn("Max sync depth reached at {}, stopping", remoteUrl);
            return;
        }

        for (DirectoryEntry entry : entries) {
            String entryUrl = remoteUrl + entry.name;
            Path localPath = localDir.resolve(entry.name.replace("/", ""));

            if (entry.isDirectory) {
                String subUrl = entryUrl.endsWith("/") ? entryUrl : entryUrl + "/";
                try {
                    Files.createDirectories(localPath);
                } catch (IOException e) {
                    LOG.error("Failed to create directory: {}", localPath, e);
                    failedFiles++;
                    continue;
                }
                // 子目录列举失败只记日志，不中断整体同步
                List<DirectoryEntry> subEntries = listRemoteDirectory(subUrl);
                if (subEntries != null) {
                    syncEntries(subEntries, subUrl, localPath, depth + 1);
                } else {
                    LOG.warn("Failed to list remote subdirectory: {}, skipping", subUrl);
                }
            } else if (entry.name.endsWith(".json")) {
                syncFile(entryUrl, localPath);
            }
        }
    }

    /**
     * 同步单个文件（增量：基于 MD5 比对）
     */
    private void syncFile(String remoteUrl, Path localPath) {
        try {
            byte[] remoteContent = fetchBytes(remoteUrl);
            if (remoteContent == null) {
                failedFiles++;
                return;
            }

            // 比对本地文件内容
            if (Files.exists(localPath)) {
                byte[] localContent = Files.readAllBytes(localPath);
                if (md5Hex(localContent).equals(md5Hex(remoteContent))) {
                    skippedFiles++;
                    return;
                }
            }

            // 写入/覆盖本地文件
            Files.createDirectories(localPath.getParent());
            Files.write(localPath, remoteContent);
            syncedFiles++;
            LOG.debug("Synced: {}", localPath);
        } catch (IOException e) {
            LOG.error("Failed to sync file: {}", remoteUrl, e);
            failedFiles++;
        }
    }

    /**
     * 解析远程目录列表（Apache/Nginx Index）
     *
     * @return 目录条目列表，失败返回 null
     */
    List<DirectoryEntry> listRemoteDirectory(String urlStr) {
        try {
            String html = fetchString(urlStr);
            if (html == null) {
                return null;
            }
            return parseDirectoryListing(html);
        } catch (IOException e) {
            LOG.error("Failed to list remote directory: {}", urlStr, e);
            return null;
        }
    }

    /**
     * 解析 Apache/Nginx 目录列表 HTML，提取链接
     */
    static List<DirectoryEntry> parseDirectoryListing(String html) {
        List<DirectoryEntry> entries = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(html);

        while (matcher.find()) {
            String href = matcher.group(1);
            String text = matcher.group(2).trim();

            // 跳过父目录链接和查询参数链接
            if (href.equals("../") || href.equals("./") || href.startsWith("/")
                    || href.startsWith("?") || href.startsWith("#")
                    || href.contains("://")) {
                continue;
            }
            // text 为 ".." 也跳过
            if ("..".equals(text)) {
                continue;
            }

            boolean isDir = href.endsWith("/");
            String name = isDir ? href.substring(0, href.length() - 1) : href;

            // 跳过空名称
            if (name.isEmpty()) {
                continue;
            }

            entries.add(new DirectoryEntry(name, isDir));
        }
        return entries;
    }

    /**
     * 获取远程 URL 内容为字符串
     */
    private String fetchString(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOG.warn("HTTP {} for {}", code, urlStr);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(4096);
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 获取远程 URL 内容为字节数组
     */
    private byte[] fetchBytes(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                LOG.warn("HTTP {} for {}", code, urlStr);
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    /**
     * 计算字节数组的 MD5 十六进制摘要
     */
    static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 始终可用
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    // ------------------------------------------------------------------ Getters

    public int getSyncedFiles() {
        return syncedFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Path getLocalRoot() {
        return localRoot;
    }

    /**
     * 目录列表条目
     */
    static class DirectoryEntry {
        final String name;
        final boolean isDirectory;

        DirectoryEntry(String name, boolean isDirectory) {
            this.name = name;
            this.isDirectory = isDirectory;
        }
    }
}
