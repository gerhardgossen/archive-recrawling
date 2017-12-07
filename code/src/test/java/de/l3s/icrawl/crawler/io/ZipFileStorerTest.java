package de.l3s.icrawl.crawler.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Ignore;
import org.junit.Test;

import de.l3s.icrawl.crawler.CrawlUrl;
import de.l3s.icrawl.crawler.CrawledResource;
import de.l3s.icrawl.crawler.io.ZipFileStorer;
import de.l3s.icrawl.snapshots.Snapshot;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class ZipFileStorerTest {

    @Test
    @Ignore
    public void testStore() throws IOException {
        String url = "http://example.org";
        String content = "content";
        Snapshot snapshot = new Snapshot(url, ZonedDateTime.now(), 200, "text/html", new HashMap<>(), content);
        CrawledResource resource = new CrawledResource(CrawlUrl.fromSeed(url, 1f), snapshot, 1f, null);
        File outFile = File.createTempFile("export-", ".zip");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile));
                ZipFileStorer storer = new ZipFileStorer(os, 10)) {
            storer.store(resource);
        }

        try (ZipInputStream is = new ZipInputStream(new BufferedInputStream(new FileInputStream(outFile)))) {
            ZipEntry record = is.getNextEntry();
            assertThat(record, is(notNullValue()));
            assertThat(record.getName(), is("0.html"));
            int len = is.read(new byte[128]);
            assertThat(len, is(content.length()));
            is.closeEntry();

            record = is.getNextEntry();
            assertThat(record, is(notNullValue()));
            assertThat(record.getName(), is("urls.csv"));
            is.closeEntry();

            record = is.getNextEntry();
            assertThat(record, is(nullValue()));
        }
        outFile.delete();
    }

}
