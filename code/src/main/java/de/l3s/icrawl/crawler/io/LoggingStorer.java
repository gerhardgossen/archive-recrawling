package de.l3s.icrawl.crawler.io;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.l3s.icrawl.crawler.CrawlUrl;
import de.l3s.icrawl.crawler.CrawledResource;

public class LoggingStorer implements ResultStorer {
    private static final Logger logger = LoggerFactory.getLogger(LoggingStorer.class);

    @Override
    public void store(CrawledResource resource) {
        logger.info("Storing resource '{}', priority={}", resource);
    }

    @Override
    public void storeNotFound(CrawlUrl url) {
        logger.info("Not found resource '{}'", url);
    }

    @Override
    public void close() throws IOException {
        logger.info("Crawl finished");
    }

}
