package com.crawler.robots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for robots.txt rules, keyed by domain.
 * Fetches and parses on first access, then returns the cached result.
 */
public class RobotsCache {

    private static final Logger log = LoggerFactory.getLogger(RobotsCache.class);

    private final ConcurrentHashMap<String, RobotsRules> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final String userAgent;
    private final int timeoutSeconds;

    public RobotsCache(String userAgent, int timeoutSeconds) {
        this.userAgent = userAgent;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public RobotsRules getRules(String scheme, String domain) {
        String key = scheme + "://" + domain;
        return cache.computeIfAbsent(key, k -> fetch(scheme, domain));
    }

    public boolean isAllowed(String scheme, String domain, String path) {
        RobotsRules rules = getRules(scheme, domain);
        // If we couldn't fetch robots.txt, allow by default
        if (rules.isFetchFailed()) return true;
        return !rules.isDisallowed(path);
    }

    private RobotsRules fetch(String scheme, String domain) {
        String robotsUrl = scheme + "://" + domain + "/robots.txt";
        log.debug("Fetching robots.txt from {}", robotsUrl);
        RobotsRules rules = new RobotsRules();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parse(resp.body(), rules);
            }
            // 404 / other codes → no restrictions
        } catch (IOException | InterruptedException e) {
            log.warn("Could not fetch robots.txt for {}: {}", domain, e.getMessage());
            rules.setFetchFailed(true);
        }
        return rules;
    }

    /**
     * Minimal robots.txt parser — respects the wildcard (*) and our specific user-agent.
     */
    private void parse(String text, RobotsRules rules) {
        boolean inRelevantBlock = false;
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;

            String field = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();

            switch (field) {
                case "user-agent" -> {
                    String agent = value.toLowerCase();
                    inRelevantBlock = agent.equals("*") || userAgent.toLowerCase().contains(agent);
                }
                case "disallow" -> {
                    if (inRelevantBlock && !value.isEmpty()) rules.addDisallowed(value);
                }
                case "crawl-delay" -> {
                    if (inRelevantBlock) {
                        try {
                            rules.setCrawlDelayMs((long)(Double.parseDouble(value) * 1000));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }
}
