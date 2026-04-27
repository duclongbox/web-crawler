package com.crawler.model;

public class CrawlTask implements Comparable<CrawlTask> {

    private final String url;
    private final int depth;

    public CrawlTask(String url, int depth) {
        this.url = url;
        this.depth = depth;
    }

    public String getUrl() { return url; }
    public int getDepth() { return depth; }

    /** BFS ordering: shallower pages have higher priority. */
    @Override
    public int compareTo(CrawlTask other) {
        return Integer.compare(this.depth, other.depth);
    }

    @Override
    public String toString() {
        return "CrawlTask{url='" + url + "', depth=" + depth + '}';
    }
}
