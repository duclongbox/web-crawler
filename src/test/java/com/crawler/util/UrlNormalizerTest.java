package com.crawler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    @Test
    void lowercasesSchemeAndHost() {
        assertEquals("https://example.com/path", UrlNormalizer.normalize("HTTPS://EXAMPLE.COM/path"));
    }

    @Test
    void removesFragment() {
        assertEquals("https://example.com/page", UrlNormalizer.normalize("https://example.com/page#section"));
    }

    @Test
    void removesDefaultHttpPort() {
        assertEquals("http://example.com/", UrlNormalizer.normalize("http://example.com:80/"));
    }

    @Test
    void removesDefaultHttpsPort() {
        assertEquals("https://example.com/", UrlNormalizer.normalize("https://example.com:443/"));
    }

    @Test
    void keepsNonDefaultPort() {
        assertEquals("http://example.com:8080/", UrlNormalizer.normalize("http://example.com:8080/"));
    }

    @Test
    void sortsQueryParams() {
        String result = UrlNormalizer.normalize("https://example.com/search?z=1&a=2");
        assertEquals("https://example.com/search?a=2&z=1", result);
    }

    @Test
    void removesTrailingSlashFromPath() {
        assertEquals("https://example.com/path", UrlNormalizer.normalize("https://example.com/path/"));
    }

    @Test
    void keepsRootSlash() {
        assertEquals("https://example.com/", UrlNormalizer.normalize("https://example.com/"));
    }

    @Test
    void returnsNullForNonHttpScheme() {
        assertNull(UrlNormalizer.normalize("ftp://example.com/file"));
    }

    @Test
    void returnsNullForNull() {
        assertNull(UrlNormalizer.normalize(null));
    }

    @Test
    void extractsDomain() {
        assertEquals("example.com", UrlNormalizer.extractDomain("https://example.com/path"));
    }

    @Test
    void resolvesRelativeUrl() {
        String resolved = UrlNormalizer.resolve("https://example.com/dir/page.html", "../other.html");
        assertEquals("https://example.com/other.html", resolved);
    }
}
