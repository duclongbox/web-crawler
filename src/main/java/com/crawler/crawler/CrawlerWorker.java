package com.crawler.crawler;

import com.crawler.config.CrawlerConfig;
import com.crawler.db.UrlDao;
import com.crawler.model.CrawlTask;
import com.crawler.model.ParseTask;
import com.crawler.queue.UrlFrontier;
import com.crawler.robots.RobotsCache;
import com.crawler.storage.StorageService;
import com.crawler.util.ContentHasher;
import com.crawler.util.UrlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches one URL at a time from the URL frontier, downloads HTML,
 * persists it to storage, and hands off a ParseTask to the parser queue.
 */
public class CrawlerWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CrawlerWorker.class);

    private final UrlFrontier frontier;
    private final BlockingQueue<ParseTask> parseQueue;
    private final UrlDao urlDao;
    private final StorageService storage;
    private final RobotsCache robotsCache;
    private final RateLimiter rateLimiter;
    private final CrawlerConfig config;
    private final AtomicBoolean running;
    private final AtomicInteger pagesCrawled;
    private final HttpClient httpClient;

    public CrawlerWorker(UrlFrontier frontier,
                         BlockingQueue<ParseTask> parseQueue,
                         UrlDao urlDao,
                         StorageService storage,
                         RobotsCache robotsCache,
                         RateLimiter rateLimiter,
                         CrawlerConfig config,
                         AtomicBoolean running,
                         AtomicInteger pagesCrawled) {
        this.frontier     = frontier;
        this.parseQueue   = parseQueue;
        this.urlDao       = urlDao;
        this.storage      = storage;
        this.robotsCache  = robotsCache;
        this.rateLimiter  = rateLimiter;
        this.config       = config;
        this.running      = running;
        this.pagesCrawled = pagesCrawled;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void run() {
        log.info("CrawlerWorker started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                CrawlTask task = frontier.poll(2, TimeUnit.SECONDS);
                if (task == null) continue;

                if (pagesCrawled.get() >= config.getMaxPages()) {
                    log.info("Max pages ({}) reached, stopping crawler worker", config.getMaxPages());
                    running.set(false);
                    break;
                }

                process(task);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("CrawlerWorker stopped: {}", Thread.currentThread().getName());
    }

    private void process(CrawlTask task) throws InterruptedException {
        String url = task.getUrl();
        String domain = UrlNormalizer.extractDomain(url);
        if (domain == null) { urlDao.updateFailed(url); return; }

        // Robots.txt check
        try {
            URI uri = URI.create(url);
            if (!robotsCache.isAllowed(uri.getScheme(), domain, uri.getPath())) {
                log.debug("Blocked by robots.txt: {}", url);
                urlDao.updateFailed(url);
                return;
            }
        } catch (IllegalArgumentException e) {
            urlDao.updateFailed(url);
            return;
        }

        // Honour robots Crawl-delay or the configured politeness delay
        long delay = config.getPolitenessDelayMs();
        try {
            URI uri = URI.create(url);
            long robotsDelay = robotsCache.getRules(uri.getScheme(), domain).getCrawlDelayMs();
            if (robotsDelay > 0) delay = Math.max(delay, robotsDelay);
        } catch (IllegalArgumentException ignored) {}
        rateLimiter.acquire(domain, delay);

        // Fetch with retry
        byte[] html = fetchWithRetry(url);
        if (html == null) { urlDao.updateFailed(url); return; }

        // Persist HTML
        String urlHash = ContentHasher.md5(url);
        String storagePath;
        try {
            storagePath = storage.save(domain, urlHash, html);
        } catch (IOException e) {
            log.error("Storage save failed for {}: {}", url, e.getMessage());
            urlDao.updateFailed(url);
            return;
        }

        pagesCrawled.incrementAndGet();
        urlDao.updateFetched(url, storagePath);
        parseQueue.put(new ParseTask(url, storagePath, task.getDepth()));
        log.info("[{}] Crawled ({} total): {}", Thread.currentThread().getName(), pagesCrawled.get(), url);
    }

    private byte[] fetchWithRetry(String url) throws InterruptedException {
        int attempts = 0;
        while (attempts <= config.getMaxRetries()) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", config.getUserAgent())
                        .timeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp.body();
                log.warn("HTTP {} for {}", resp.statusCode(), url);
                return null;  // don't retry on 4xx/5xx
            } catch (IOException e) {
                attempts++;
                if (attempts > config.getMaxRetries()) {
                    log.error("All retries exhausted for {}: {}", url, e.getMessage());
                    return null;
                }
                log.warn("Fetch attempt {}/{} failed for {}: {}", attempts, config.getMaxRetries(), url, e.getMessage());
                Thread.sleep(config.getRetryDelayMs());
            }
        }
        return null;
    }
}
