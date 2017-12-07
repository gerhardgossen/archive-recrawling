package de.l3s.icrawl.crawler.scheduling;

import java.util.concurrent.atomic.AtomicLong;

public class NumberOfUrlsStoppingCriterion extends StoppingCriterion {
    private final AtomicLong counter = new AtomicLong();
    private final long maxUrls;

    public NumberOfUrlsStoppingCriterion(long maxUrls) {
        this.maxUrls = maxUrls;
    }

    @Override
    public void updateSuccess(double relevance) {
        long newValue = counter.incrementAndGet();
        if (newValue >= maxUrls) {
            stop();
        }
    }

    @Override
    public float getProgress() {
        return counter.floatValue() / maxUrls;
    }
}
