package de.l3s.gossen.crawler.frontier;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.google.common.hash.BloomFilter;

import de.l3s.gossen.crawler.CrawlUrl;

import static com.codahale.metrics.MetricRegistry.name;
import static com.google.common.hash.Funnels.stringFunnel;
import static java.nio.charset.StandardCharsets.US_ASCII;

public abstract class BaseFrontier implements Frontier, Closeable {
    protected final BloomFilter<CharSequence> seenUrls = BloomFilter.create(stringFunnel(US_ASCII), 1_000_000);
    private final MetricRegistry metrics;
    protected final Meter incoming;
    protected final Meter outgoing;
    private Counter size;
    protected Meter emptyQueue;
    protected long totalIncoming = 0;
    protected final Object lock = new Object();

    public BaseFrontier(MetricRegistry metrics) {
        this.metrics = metrics;
        incoming = metrics.meter(name(getClass(), "incomingUrls"));
        outgoing = metrics.meter(name(getClass(), "outgoingUrls"));
        emptyQueue = metrics.meter(name(getClass(), "emptyQueue"));
        size = metrics.counter(name(getClass(), "size"));
        metrics.register(name(getClass(), "seenUrlsFpp"), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return seenUrls.expectedFpp();
            }
        });
        metrics.register(name(getClass(), "unseenRate"), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(incoming.getCount(), totalIncoming);
            }
        });
    }

    @Override
    public void push(Collection<CrawlUrl> urls) {
        synchronized (lock) {
            totalIncoming += urls.size();
            for (CrawlUrl url : urls) {
                if (!seenUrls.mightContain(url.getUrl())) {
                    incoming.mark();
                    size.inc();
                    pushInternal(url);
                    seenUrls.put(url.getUrl());
                }
            }
        }
    }

    @Override
    public Optional<CrawlUrl> pop() {
        synchronized (lock) {
            Optional<CrawlUrl> internal = popInternal();
            if (internal.isPresent()) {
                outgoing.mark();
                size.dec();
            } else {
                emptyQueue.mark();
            }
            return internal;
        }
    }

    protected abstract Optional<CrawlUrl> popInternal();

    protected abstract void pushInternal(CrawlUrl url);

    @Override
    public void close() throws IOException {
        metrics.removeMatching(new ClassMetricFilter(getClass()));
    }

}
