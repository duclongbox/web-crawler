package com.crawler.storage;

import java.io.IOException;

public interface StorageService {
    /**
     * Saves raw HTML content and returns the storage path/key.
     * @param domain  the site's domain (used for directory organisation)
     * @param urlHash MD5/SHA-256 hash of the URL
     * @param html    raw HTML bytes
     */
    String save(String domain, String urlHash, byte[] html) throws IOException;

    /** Reads back previously stored HTML. */
    byte[] load(String storagePath) throws IOException;

    /** Saves extracted plain text and returns the storage path. */
    String saveText(String domain, String urlHash, String text) throws IOException;
}
