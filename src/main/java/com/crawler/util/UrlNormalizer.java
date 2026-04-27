package com.crawler.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.TreeMap;

public final class UrlNormalizer {

    private UrlNormalizer() {}

    /**
     * Normalizes a URL:
     * - Lowercases scheme and host
     * - Removes fragment (#...)
     * - Sorts query parameters alphabetically
     * - Removes trailing slash from path (except bare root)
     * - Strips default ports (80 for http, 443 for https)
     * - Resolves relative URLs against a base when provided
     */
    public static String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        try {
            URI uri = new URI(rawUrl.trim()).normalize();

            String scheme = uri.getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase();

            // Only handle http/https
            if (!scheme.equals("http") && !scheme.equals("https")) return null;

            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase();

            int port = uri.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = "/";
            // Remove trailing slash unless it is the root
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            String sortedQuery = sortQueryParams(uri.getRawQuery());

            URI normalized = new URI(scheme, null, host, port, path, null, null);
            String result = normalized.toASCIIString();
            if (sortedQuery != null && !sortedQuery.isEmpty()) result += "?" + sortedQuery;
            return result;

        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static String resolve(String base, String href) {
        if (href == null || href.isBlank()) return null;
        try {
            URI baseUri = new URI(base);
            URI resolved = baseUri.resolve(href.trim());
            return normalize(resolved.toString());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static String extractDomain(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String sortQueryParams(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return null;
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String param : rawQuery.split("&")) {
            String[] kv = param.split("=", 2);
            sorted.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k);
            if (!v.isEmpty()) sb.append('=').append(v);
        });
        return sb.toString();
    }
}
