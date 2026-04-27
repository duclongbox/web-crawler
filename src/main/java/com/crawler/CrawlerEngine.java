package com.crawler;

import com.crawler.config.CrawlerConfig;
import com.crawler.crawler.CrawlerWorker;
import com.crawler.crawler.RateLimiter;
import com.crawler.db.DatabaseManager;
import com.crawler.db.UrlDao;
import com.crawler.model.CrawlTask;
import com.crawler.model.ParseTask;
import com.crawler.model.UrlRecord;
import com.crawler.parser.ParserWorker;
import com.crawler.queue.UrlFrontier;
import com.crawler.robots.RobotsCache;
import com.crawler.storage.LocalStorageService;
import com.crawler.storage.StorageService;
import com.crawler.util.UrlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wires all components together and manages the lifecycle of thread pools.
 */
public class CrawlerEngine {

    private static final Logger log = LoggerFactory.getLogger(CrawlerEngine.class);

    private final CrawlerConfig config;
    private final DatabaseManager dbManager;
    private final UrlDao urlDao;
    private final StorageService storage;
    private final UrlFrontier frontier;
    private final BlockingQueue<ParseTask> parseQueue;
    private final RobotsCache robotsCache;
    private final RateLimiter rateLimiter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private ExecutorService crawlerPool;
    private ExecutorService parserPool;

    public CrawlerEngine(CrawlerConfig config) {
        this.config    = config;
        this.dbManager = new DatabaseManager(config.getMongoUri(), config.getMongoDatabase());
        this.dbManager.init();
        this.urlDao    = new UrlDao(dbManager);
        this.storage   = new LocalStorageService(config.getStorageBasePath());
        this.frontier  = new UrlFrontier(config.getQueueMaxSize());
        this.parseQueue = new LinkedBlockingQueue<>(config.getQueueMaxSize());
        this.robotsCache = new RobotsCache(config.getUserAgent(), config.getRequestTimeoutSeconds());
        this.rateLimiter = new RateLimiter();
    }

    /** Seeds the crawler with an initial list of URLs and starts processing. */
    public void start(List<String> seedUrls) {
        log.info("CrawlerEngine starting with {} seed URL(s)", seedUrls.size());
        running.set(true);

        // Seed the frontier
        for (String raw : seedUrls) {
            String normalized = UrlNormalizer.normalize(raw);
            if (normalized == null) { log.warn("Invalid seed URL skipped: {}", raw); continue; }
            String domain = UrlNormalizer.extractDomain(normalized);
            urlDao.insertIfAbsent(normalized, domain);
            frontier.offer(new CrawlTask(normalized, 0));
        }

        // Start crawler thread pool
        crawlerPool = Executors.newFixedThreadPool(config.getCrawlerThreads(),
                r -> { Thread t = new Thread(r); t.setName("crawler-" + t.getId()); return t; });
        for (int i = 0; i < config.getCrawlerThreads(); i++) {
            crawlerPool.submit(new CrawlerWorker(
                    frontier, parseQueue, urlDao, storage,
                    robotsCache, rateLimiter, config, running, pagesCrawled));
        }

        // Start parser thread pool
        parserPool = Executors.newFixedThreadPool(config.getParserThreads(),
                r -> { Thread t = new Thread(r); t.setName("parser-" + t.getId()); return t; });
        for (int i = 0; i < config.getParserThreads(); i++) {
            parserPool.submit(new ParserWorker(
                    parseQueue, frontier, urlDao, storage, config, running));
        }

        // Monitor thread: detect idle (both queues empty) and trigger graceful shutdown
        Thread monitor = new Thread(this::monitorIdle, "crawler-monitor");
        monitor.setDaemon(true);
        monitor.start();

        // Register JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));

        log.info("CrawlerEngine running — crawlerThreads={}, parserThreads={}",
                config.getCrawlerThreads(), config.getParserThreads());
    }

    private void monitorIdle() {
        long idleMs = config.getIdleShutdownSeconds() * 1000L;
        long idleSince = -1;
        while (running.get()) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            boolean queuesEmpty = frontier.isEmpty() && parseQueue.isEmpty();
            if (queuesEmpty) {
                if (idleSince < 0) idleSince = System.currentTimeMillis();
                else if (System.currentTimeMillis() - idleSince >= idleMs) {
                    log.info("Both queues idle for {}s — initiating graceful shutdown", config.getIdleShutdownSeconds());
                    shutdown();
                    return;
                }
            } else {
                idleSince = -1;
            }
        }
    }

    /** Graceful shutdown: signals workers, waits for completion, logs stats. */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;  // already shutting down
        log.info("Shutdown initiated...");

        crawlerPool.shutdown();
        parserPool.shutdown();
        try {
            if (!crawlerPool.awaitTermination(30, TimeUnit.SECONDS))
                crawlerPool.shutdownNow();
            if (!parserPool.awaitTermination(30, TimeUnit.SECONDS))
                parserPool.shutdownNow();
        } catch (InterruptedException e) {
            crawlerPool.shutdownNow();
            parserPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logFinalStats();
        dbManager.close();
        log.info("CrawlerEngine shut down cleanly.");
    }

    public void awaitTermination() throws InterruptedException {
        if (crawlerPool != null) crawlerPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        if (parserPool  != null) parserPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private void logFinalStats() {
        long completed = urlDao.countByStatus(UrlRecord.Status.COMPLETED);
        long failed    = urlDao.countByStatus(UrlRecord.Status.FAILED);
        long pending   = urlDao.countByStatus(UrlRecord.Status.PENDING);
        log.info("=== Final Stats === completed={}, failed={}, pending={}, pagesCrawled={}",
                completed, failed, pending, pagesCrawled.get());
    }
}
