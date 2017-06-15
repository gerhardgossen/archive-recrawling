package de.l3s.gossen.crawler.io;

import java.io.Closeable;
import java.io.IOException;

import de.l3s.gossen.crawler.CrawlUrl;
import de.l3s.gossen.crawler.CrawledResource;

public interface ResultStorer extends Closeable {

    public static interface Factory {
        ResultStorer get(String name) throws IOException;
    }

    String OUTPUT_DIRECTORY = "de_l3s_gossen_crawler_outputDirectory";

    void store(CrawledResource resource);

    void storeNotFound(CrawlUrl url);

}
