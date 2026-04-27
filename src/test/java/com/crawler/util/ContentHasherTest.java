package com.crawler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentHasherTest {

    @Test
    void md5IsDeterministic() {
        String h1 = ContentHasher.md5("hello world");
        String h2 = ContentHasher.md5("hello world");
        assertEquals(h1, h2);
    }

    @Test
    void md5DiffersForDifferentInput() {
        assertNotEquals(ContentHasher.md5("a"), ContentHasher.md5("b"));
    }

    @Test
    void md5Is32Chars() {
        assertEquals(32, ContentHasher.md5("test").length());
    }

    @Test
    void sha256Is64Chars() {
        assertEquals(64, ContentHasher.sha256("test").length());
    }
}
