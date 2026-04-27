package com.crawler.model;

import java.time.Instant;

public class UrlRecord {

    public enum Status { PENDING, FETCHED, COMPLETED, FAILED }

    private String urlId;
    private String url;
    private String storagePath;
    private Status status;
    private Instant lastCrawlTime;
    private String contentHash;
    private String domain;
    private int failCount;

    public UrlRecord() {}

    public UrlRecord(String urlId, String url, String domain) {
        this.urlId = urlId;
        this.url = url;
        this.domain = domain;
        this.status = Status.PENDING;
    }

    public String getUrlId() { return urlId; }
    public void setUrlId(String urlId) { this.urlId = urlId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getLastCrawlTime() { return lastCrawlTime; }
    public void setLastCrawlTime(Instant lastCrawlTime) { this.lastCrawlTime = lastCrawlTime; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
}
