package de.l3s.icrawl.snapshots;

import java.time.ZonedDateTime;
import java.util.Objects;

public class SnaphotLocation {
    private final String url;
    private final ZonedDateTime crawlTime;
    private final String warcFile;
    private final long warcFileOffset;
    private final long length;
    private final String mimeType;
    private final String signature;

    public SnaphotLocation(String url, ZonedDateTime crawlTime, String warcFile, long warcFileOffset, long length,
            String mimeType, String signature) {
        this.url = url;
        this.crawlTime = crawlTime;
        this.warcFile = warcFile;
        this.warcFileOffset = warcFileOffset;
        this.length = length;
        this.mimeType = mimeType;
        this.signature = signature;
    }

    public String getUrl() {
        return url;
    }

    public ZonedDateTime getCrawlTime() {
        return crawlTime;
    }

    public String getWarcFile() {
        return warcFile;
    }

    public long getWarcFileOffset() {
        return warcFileOffset;
    }

    public long getLength() {
        return length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SnaphotLocation)) {
            return false;
        }
        SnaphotLocation o = (SnaphotLocation) obj;
        return Objects.equals(url, o.url) && Objects.equals(crawlTime, o.crawlTime)
                && Objects.equals(warcFile, o.warcFile)
                && Objects.equals(warcFileOffset, o.warcFileOffset)
                && Objects.equals(length, o.length) && Objects.equals(mimeType, o.mimeType)
                && Objects.equals(signature, o.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, crawlTime, warcFile, warcFileOffset, length, mimeType, signature);
    }

    @Override
    public String toString() {
        return String.format("%s@%s (%s:%d+%d)", url, crawlTime, warcFile, warcFileOffset, length);
    }

    public SnaphotLocation withWarcFile(String newWarcFile) {
        return new SnaphotLocation(url, crawlTime, newWarcFile, warcFileOffset, length, mimeType, signature);
    }
}
