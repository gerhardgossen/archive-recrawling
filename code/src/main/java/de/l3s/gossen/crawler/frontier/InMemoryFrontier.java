package de.l3s.gossen.crawler.frontier;

import java.util.LinkedList;
import java.util.Optional;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import de.l3s.gossen.crawler.CrawlUrl;

import static com.codahale.metrics.MetricRegistry.name;

public class InMemoryFrontier extends BaseFrontier implements Frontier {
    private final LinkedList<CrawlUrl> queue = new LinkedList<>();

    public InMemoryFrontier(MetricRegistry metrics) {
        super(metrics);
        metrics.register(name(getClass(), "size"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return queue.size();
            }
        });
    }

    @Override
    protected void pushInternal(CrawlUrl url) {
        queue.offer(url);
    }

    @Override
    protected Optional<CrawlUrl> popInternal() {
        return Optional.ofNullable(queue.poll());
    }

}
