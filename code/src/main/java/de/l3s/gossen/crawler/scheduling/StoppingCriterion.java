package de.l3s.gossen.crawler.scheduling;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;

public abstract class StoppingCriterion implements Gauge<Float> {

    public interface StopListener {
        void stop();
    }

    private static final Logger logger = LoggerFactory.getLogger(StoppingCriterion.class);
    private final List<StopListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean didNotify = new AtomicBoolean(false);

    public void updateSuccess(double relevance) {
        // default: do nothing
    }

    public void updateFailure() {
        // default: do nothing
    }

    public void updateEmptyQueue() {
        // default: do nothing
    }

    public void updateIrrelevant(double relevance) {
        // default: do nothing
    }

    public void addListener(StopListener listener) {
        listeners.add(listener);
    }

    public boolean removeListener(StopListener listener) {
        return listeners.remove(listener);
    }

    protected void stop() {
        if (!didNotify.compareAndSet(false, true)) {
            logger.debug("Already did notification, skipping");
            return;
        }
        for (StopListener listener : listeners) {
            listener.stop();
        }
        listeners.clear();
    }

    @Override
    public Float getValue() {
        return getProgress();
    }

    public abstract float getProgress();

}
