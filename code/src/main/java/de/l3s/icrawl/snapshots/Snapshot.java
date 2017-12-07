package de.l3s.icrawl.snapshots;

import java.time.ZonedDateTime;
import java.util.Map;

public class Snapshot {

    private final String originalUrl;
    private final ZonedDateTime crawlTime;
    private final int status;
    private final String mimeType;
    private final Map<String, String> headers;
    private final Object content;

    public Snapshot(String originalUrl, ZonedDateTime crawlTime, int status, String mimeType,
            Map<String, String> headers, Object content) {
        this.originalUrl = originalUrl;
        this.crawlTime = crawlTime;
        this.status = status;
        this.mimeType = mimeType;
        this.headers = headers;
        this.content = content;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public ZonedDateTime getCrawlTime() {
        return crawlTime;
    }

    public int getStatus() {
        return status;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return String.format("%s@%s (%d, %s)", originalUrl, crawlTime, status, mimeType);
    }
}
