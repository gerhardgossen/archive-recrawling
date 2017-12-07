package de.l3s.icrawl.snapshots;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.archive.url.URLKeyMaker;
import org.archive.util.io.RuntimeIOException;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import static com.codahale.metrics.MetricRegistry.name;

public abstract class BaseSnapshotsLocator implements SnapshotsLocator {
    private final URLKeyMaker keyMaker;
    private final Meter urlRate;
    private final Histogram snapshots;
    private final Timer timer;

    public BaseSnapshotsLocator(URLKeyMaker keyMaker, MetricRegistry metrics) {
        this.keyMaker = keyMaker;
        urlRate = metrics.meter(name(getClass(), "urls"));
        snapshots = metrics.histogram(name(getClass(), "snapshots"));
        timer = metrics.timer(name(getClass(), "ioTime"));
    }

    @Override
    public Iterable<SnaphotLocation> findLocations(String url) {
        String surt = makeSurt(url);
        try (Timer.Context context = timer.time()) {
            List<SnaphotLocation> result = findInternal(surt);
            urlRate.mark();
            snapshots.update(result.size());
            return result;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public Optional<SnaphotLocation> findLocation(String url, ZonedDateTime crawlTime) {
        String surt = makeSurt(url);
        try (Timer.Context context = timer.time()) {
            Optional<SnaphotLocation> result = findOneInternal(surt, crawlTime);
            urlRate.mark();
            snapshots.update(result.isPresent() ? 1 : 0);
            return result;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    protected abstract List<SnaphotLocation> findInternal(String surt) throws IOException;

    protected abstract Optional<SnaphotLocation> findOneInternal(String surt, ZonedDateTime crawlTime)
            throws IOException;

    private String makeSurt(String url) {
        try {
            return keyMaker.makeKey(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid URL: " + url, e);
        }
    }


}
