package de.l3s.icrawl.crawler.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.l3s.icrawl.crawler.CrawlUrl;
import de.l3s.icrawl.crawler.CrawledResource;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ZipFileStorer implements ResultStorer {
    private static final Logger LOG = LoggerFactory.getLogger(ZipFileStorer.class);

    private static class StoredUrl {
        private final String url;
        private final String path;
        private final double relevance;
        private final ZonedDateTime crawlTime;
        private final String fileName;
        private final ZonedDateTime modifiedDate;

        public StoredUrl(CrawledResource resource, String fileName) {
            url = resource.getUrl();
            path = resource.getPath();
            relevance = resource.getRelevance();
            crawlTime = resource.getResource().getCrawlTime();
            this.fileName = fileName;
            modifiedDate = resource.getModifiedDate();
        }
    }

    private final ZipOutputStream os;
    private final Object successLock = new Object();
    private final Object failLock = new Object();
    private final Object writeLock = new Object();
    private final List<StoredUrl> successfullUrls;
    private final List<CrawlUrl> failedUrls;

    public ZipFileStorer(OutputStream os, int numResults) {
        this.os = new ZipOutputStream(os, UTF_8);
        this.successfullUrls = new ArrayList<>(numResults);
        this.failedUrls = new ArrayList<>(numResults);
    }

    @Override
    public void store(CrawledResource resource) {
        synchronized (successLock) {
            try {
                int index = successfullUrls.size();
                String fileName = index + ".html";
                successfullUrls.add(new StoredUrl(resource, fileName));
                Object content = resource.getResource().getContent();
                byte[] bytes;
                if (content instanceof String) {
                    bytes = ((String) content).getBytes(UTF_8);
                } else {
                    bytes = (byte[]) content;
                }
                Instant crawlTime = resource.getResource().getCrawlTime().toInstant();
                writeZipEntry(fileName, crawlTime, bytes);
            } catch (IOException e) {
                LOG.info("Exception while storing result '{}':", resource, e);
            }
        }
    }

    private void writeZipEntry(String fileName, Instant timestamp, byte[] bytes) throws IOException {
        synchronized (writeLock) {
            ZipEntry entry = new ZipEntry(fileName);
            entry.setCreationTime(FileTime.from(timestamp));
            os.putNextEntry(entry);
            os.write(bytes);
            os.closeEntry();
        }
    }

    @Override
    public void storeNotFound(CrawlUrl url) {
        synchronized (failLock) {
            failedUrls.add(url);
        }
    }

    @Override
    public void close() throws IOException {
        writeTOC();
        writeMissing();
        os.close();
    }

    private void writeMissing() throws IOException {
        if (failedUrls.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(failedUrls.size() * 200);
        sb.append("url\tpath\tpriority\n");
        for (CrawlUrl failedUrl : failedUrls) {
            sb.append(failedUrl.getUrl())
                .append('\t')
                .append(failedUrl.getPath())
                .append('\t')
                .append(failedUrl.getPriority())
                .append('\n');
        }
        writeZipEntry("missing.csv", Instant.now(), sb.toString().getBytes(UTF_8));
    }

    private void writeTOC() throws IOException {
        if (successfullUrls.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(successfullUrls.size() * 200);
        sb.append("url\tpath\trelevance\tcrawlTime\tfile\tmodifiedDate\n");
        for (StoredUrl storedUrl : successfullUrls) {
            sb.append(storedUrl.url)
                .append('\t')
                .append(storedUrl.path)
                .append('\t')
                .append(storedUrl.relevance)
                .append('\t')
                .append(storedUrl.crawlTime.toString())
                .append('\t')
                .append(storedUrl.fileName)
                .append('\t')
                .append(storedUrl.modifiedDate)
                .append('\n');
        }
        writeZipEntry("urls.csv", Instant.now(), sb.toString().getBytes(UTF_8));
    }

}
