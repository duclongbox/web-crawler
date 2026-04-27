package com.crawler.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    public static final String COLLECTION = "url_records";

    private final MongoClient client;
    private final MongoDatabase database;

    public DatabaseManager(String mongoUri, String dbName) {
        log.info("Connecting to MongoDB at {} / {}", mongoUri, dbName);
        this.client   = MongoClients.create(mongoUri);
        this.database = client.getDatabase(dbName);
    }

    public void init() {
        MongoCollection<Document> col = getCollection();
        // Unique index on url — acts as the duplicate guard
        col.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
        col.createIndex(Indexes.ascending("domain"));
        col.createIndex(Indexes.ascending("status"));
        log.info("MongoDB indexes ready on collection '{}'", COLLECTION);
    }

    public MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION);
    }

    public void close() {
        client.close();
        log.info("MongoDB connection closed");
    }
}
