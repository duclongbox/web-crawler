package com.crawler.model;

/** Passed from the crawler thread pool to the parser thread pool. */
public class ParseTask {

    private final String url;
    private final String storagePath;  // local path or S3 key of raw HTML
    private final int depth;

    public ParseTask(String url, String storagePath, int depth) {
        this.url = url;
        this.storagePath = storagePath;
        this.depth = depth;
    }

    public String getUrl() { return url; }
    public String getStoragePath() { return storagePath; }
    public int getDepth() { return depth; }
}
