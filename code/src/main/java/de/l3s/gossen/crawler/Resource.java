package de.l3s.gossen.crawler;

import java.util.Map;
import java.util.Objects;

public class Resource {
    private final CrawlUrl url;
    private final Map<String, String> headers;
    private final String content;

    public Resource(CrawlUrl url, Map<String, String> headers, String content) {
        this.url = Objects.requireNonNull(url);
        this.headers = Objects.requireNonNull(headers);
        this.content = Objects.requireNonNull(content);
    }

    public CrawlUrl getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getContent() {
        return content;
    }


}
