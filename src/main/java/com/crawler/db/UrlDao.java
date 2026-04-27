package com.crawler.db;

import com.crawler.model.UrlRecord;
import com.crawler.model.UrlRecord.Status;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

/**
 * MongoDB-backed data access object for url_records.
 *
 * Document shape:
 * {
 *   _id:          ObjectId (auto),
 *   url_id:       String,
 *   url:          String  (unique index),
 *   domain:       String,
 *   storage_path: String,
 *   status:       String  ("PENDING" | "FETCHED" | "COMPLETED" | "FAILED"),
 *   content_hash: String,
 *   last_crawl_time: Long (epoch ms),
 *   fail_count:   Int
 * }
 */
public class UrlDao {

    private static final Logger log = LoggerFactory.getLogger(UrlDao.class);

    private final MongoCollection<Document> col;

    public UrlDao(DatabaseManager db) {
        this.col = db.getCollection();
    }

    /**
     * Inserts a PENDING record only if the URL does not already exist.
     * Uses the unique index on `url` — concurrent-safe via MongoDB's atomic upsert.
     *
     * @return true if a new document was inserted, false if it already existed
     */
    public boolean insertIfAbsent(String url, String domain) {
        try {
            Document doc = new Document()
                    .append("url_id", UUID.randomUUID().toString().replace("-", ""))
                    .append("url", url)
                    .append("domain", domain)
                    .append("status", Status.PENDING.name())
                    .append("fail_count", 0);
            col.insertOne(doc);
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCode() == 11000) return false;  // duplicate key
            log.error("insertIfAbsent failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    public Optional<UrlRecord> findByUrl(String url) {
        Document doc = col.find(eq("url", url)).first();
        return doc == null ? Optional.empty() : Optional.of(map(doc));
    }

    public void updateFetched(String url, String storagePath) {
        col.updateOne(eq("url", url), combine(
                set("status", Status.FETCHED.name()),
                set("storage_path", storagePath),
                set("last_crawl_time", Instant.now().toEpochMilli())
        ));
    }

    public void updateCompleted(String url, String contentHash, String parsedTextPath, String textPreview) {
        col.updateOne(eq("url", url), combine(
                set("status", Status.COMPLETED.name()),
                set("content_hash", contentHash),
                set("parsed_text_path", parsedTextPath),
                set("text_preview", textPreview),
                set("last_crawl_time", Instant.now().toEpochMilli())
        ));
    }

    public void updateFailed(String url) {
        col.updateOne(eq("url", url), combine(
                set("status", Status.FAILED.name()),
                set("last_crawl_time", Instant.now().toEpochMilli()),
                inc("fail_count", 1)
        ));
    }

    public long countByStatus(Status status) {
        return col.countDocuments(eq("status", status.name()));
    }

    private UrlRecord map(Document doc) {
        UrlRecord r = new UrlRecord();
        r.setUrlId(doc.getString("url_id"));
        r.setUrl(doc.getString("url"));
        r.setDomain(doc.getString("domain"));
        r.setStoragePath(doc.getString("storage_path"));
        String status = doc.getString("status");
        if (status != null) r.setStatus(Status.valueOf(status));
        r.setContentHash(doc.getString("content_hash"));
        Long ts = doc.getLong("last_crawl_time");
        if (ts != null) r.setLastCrawlTime(Instant.ofEpochMilli(ts));
        Integer failCount = doc.getInteger("fail_count");
        r.setFailCount(failCount != null ? failCount : 0);
        return r;
    }
}
