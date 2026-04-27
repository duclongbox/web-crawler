package com.crawler.parser;

import com.crawler.config.CrawlerConfig;
import com.crawler.db.UrlDao;
import com.crawler.model.CrawlTask;
import com.crawler.model.ParseTask;
import com.crawler.model.UrlRecord;
import com.crawler.queue.UrlFrontier;
import com.crawler.storage.StorageService;
import com.crawler.util.ContentHasher;
import com.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes ParseTasks, extracts text + links from stored HTML,
 * and feeds newly discovered URLs back into the URL frontier.
 */
public class ParserWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ParserWorker.class);

    private final BlockingQueue<ParseTask> parseQueue;
    private final UrlFrontier frontier;
    private final UrlDao urlDao;
    private final StorageService storage;
    private final CrawlerConfig config;
    private final AtomicBoolean running;

    public ParserWorker(BlockingQueue<ParseTask> parseQueue,
                        UrlFrontier frontier,
                        UrlDao urlDao,
                        StorageService storage,
                        CrawlerConfig config,
                        AtomicBoolean running) {
        this.parseQueue = parseQueue;
        this.frontier   = frontier;
        this.urlDao     = urlDao;
        this.storage    = storage;
        this.config     = config;
        this.running    = running;
    }

    @Override
    public void run() {
        log.info("ParserWorker started: {}", Thread.currentThread().getName());
        while (running.get()) {
            try {
                ParseTask task = parseQueue.poll(2, TimeUnit.SECONDS);
                if (task == null) continue;
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Drain any remaining tasks after shutdown signal
        ParseTask task;
        while ((task = parseQueue.poll()) != null) {
            process(task);
        }
        log.info("ParserWorker stopped: {}", Thread.currentThread().getName());
    }

    private void process(ParseTask task) {
        String url = task.getUrl();
        try {
            byte[] html = storage.load(task.getStoragePath());
            Document doc = Jsoup.parse(new String(html, StandardCharsets.UTF_8), url);

            // Extract text, hash it, save it
            String text = doc.body() != null ? doc.body().text() : "";
            String contentHash = ContentHasher.md5(text);
            String domain = UrlNormalizer.extractDomain(url);
            String urlHash = ContentHasher.md5(url);
            String parsedTextPath = storage.saveText(domain, urlHash, text);
            String textPreview = text.length() > 500 ? text.substring(0, 500) + "…" : text;

            // Discover and enqueue new URLs
            if (task.getDepth() < config.getMaxDepth()) {
                for (Element link : doc.select("a[href]")) {
                    String href = link.attr("abs:href");
                    String normalized = UrlNormalizer.normalize(href);
                    if (normalized == null) continue;

                    String linkDomain = UrlNormalizer.extractDomain(normalized);
                    if (linkDomain == null) continue;

                    Optional<UrlRecord> existing = urlDao.findByUrl(normalized);
                    if (existing.isPresent()) {
                        UrlRecord r = existing.get();
                        // Re-queue only genuinely failed URLs after giving up for a while
                        if (r.getStatus() != UrlRecord.Status.COMPLETED
                                && r.getStatus() != UrlRecord.Status.PENDING
                                && r.getStatus() != UrlRecord.Status.FETCHED) {
                            // FAILED — skip (retry logic lives in the DAO / future enhancement)
                        }
                        continue;
                    }

                    // New URL — persist and enqueue
                    urlDao.insertIfAbsent(normalized, linkDomain);
                    frontier.offer(new CrawlTask(normalized, task.getDepth() + 1));
                    log.debug("Discovered: {}", normalized);
                }
            }

            urlDao.updateCompleted(url, contentHash, parsedTextPath, textPreview);
            log.debug("Parsed: {}", url);

        } catch (IOException e) {
            log.error("Parser failed for {}: {}", url, e.getMessage());
            urlDao.updateFailed(url);
        }
    }
}
