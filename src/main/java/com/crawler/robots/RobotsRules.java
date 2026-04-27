package com.crawler.robots;

import java.util.ArrayList;
import java.util.List;

/** Parsed rules from a single robots.txt file. */
public class RobotsRules {

    private final List<String> disallowedPaths = new ArrayList<>();
    private long crawlDelayMs = 0;
    private boolean fetchFailed = false;

    public void addDisallowed(String path) { disallowedPaths.add(path); }
    public void setCrawlDelayMs(long ms)   { this.crawlDelayMs = ms; }
    public void setFetchFailed(boolean v)  { this.fetchFailed = v; }

    public boolean isFetchFailed()         { return fetchFailed; }
    public long getCrawlDelayMs()          { return crawlDelayMs; }

    /** Returns true if the given path is disallowed by any Disallow rule. */
    public boolean isDisallowed(String path) {
        for (String rule : disallowedPaths) {
            if (!rule.isEmpty() && path.startsWith(rule)) return true;
        }
        return false;
    }
}
