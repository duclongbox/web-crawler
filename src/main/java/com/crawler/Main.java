package com.crawler;

import com.crawler.config.CrawlerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        List<String> seeds;
        if (args.length > 0) {
            seeds = Arrays.asList(args);
        } else {
            // Default demo seeds — swap in any real URLs
            seeds = List.of(
                    "https://www.geeksforgeeks.org/dsa/breadth-first-search-or-bfs-for-a-graph/");
        }

        log.info("Starting crawler with seeds: {}", seeds);
        CrawlerConfig config = new CrawlerConfig("crawler.properties");
        CrawlerEngine engine = new CrawlerEngine(config);
        engine.start(seeds);
        engine.awaitTermination();
    }
}
