package de.l3s.gossen.crawler.frontier;

import java.io.Closeable;
import java.util.Collection;
import java.util.Optional;

import de.l3s.gossen.crawler.CrawlUrl;

public interface Frontier extends Closeable {
    void push(Collection<CrawlUrl> urls);

    Optional<CrawlUrl> pop();
}
