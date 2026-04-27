package com.crawler.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * Stores HTML files on the local filesystem.
 * Directory layout: {basePath}/{domain}/{urlHash}.html
 */
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path basePath;

    public LocalStorageService(String basePath) {
        this.basePath = Path.of(basePath);
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create storage base dir: " + basePath, e);
        }
        log.info("Local storage at {}", this.basePath.toAbsolutePath());
    }

    @Override
    public String save(String domain, String urlHash, byte[] html) throws IOException {
        // Sanitise domain to a safe directory name
        String safeDomain = domain.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = basePath.resolve(safeDomain);
        Files.createDirectories(dir);
        Path file = dir.resolve(urlHash + ".html");
        Files.write(file, html, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        String path = file.toString();
        log.debug("Saved HTML to {}", path);
        return path;
    }

    @Override
    public byte[] load(String storagePath) throws IOException {
        return Files.readAllBytes(Path.of(storagePath));
    }

    @Override
    public String saveText(String domain, String urlHash, String text) throws IOException {
        String safeDomain = domain.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = basePath.resolve(safeDomain);
        Files.createDirectories(dir);
        Path file = dir.resolve(urlHash + ".txt");
        Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Saved text to {}", file);
        return file.toString();
    }
}
