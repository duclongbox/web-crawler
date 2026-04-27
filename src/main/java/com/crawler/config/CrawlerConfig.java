package com.crawler.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class CrawlerConfig {

    private final Properties props = new Properties();

    public CrawlerConfig(String resourceName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new RuntimeException("Config resource not found: " + resourceName);
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + resourceName, e);
        }
    }

    public int getCrawlerThreads()          { return getInt("crawler.threads", 5); }
    public int getParserThreads()           { return getInt("parser.threads", 5); }
    public long getPolitenessDelayMs()      { return getLong("politeness.delay.milliseconds", 1000); }
    public int getQueueMaxSize()            { return getInt("queue.max.size", 10_000); }
    public String getStorageType()          { return props.getProperty("storage.type", "local"); }
    public String getStorageBasePath()      { return props.getProperty("storage.base.path", "./crawl_data"); }
    public String getMongoUri()             { return props.getProperty("mongo.uri", "mongodb://localhost:27017"); }
    public String getMongoDatabase()        { return props.getProperty("mongo.database", "crawler"); }
    public int getMaxDepth()                { return getInt("max.depth", 3); }
    public int getMaxPages()                { return getInt("max.pages", 1000); }
    public int getRequestTimeoutSeconds()   { return getInt("request.timeout.seconds", 10); }
    public int getMaxRetries()              { return getInt("max.retries", 3); }
    public long getRetryDelayMs()           { return getLong("retry.delay.milliseconds", 2000); }
    public String getUserAgent()            { return props.getProperty("user.agent", "JavaWebCrawler/1.0"); }
    public int getIdleShutdownSeconds()     { return getInt("idle.shutdown.seconds", 10); }

    private int getInt(String key, int defaultVal) {
        return Integer.parseInt(props.getProperty(key, String.valueOf(defaultVal)));
    }

    private long getLong(String key, long defaultVal) {
        return Long.parseLong(props.getProperty(key, String.valueOf(defaultVal)));
    }
}
