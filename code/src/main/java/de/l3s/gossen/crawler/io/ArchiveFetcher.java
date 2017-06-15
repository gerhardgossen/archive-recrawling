package de.l3s.gossen.crawler.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.apache.hadoop.conf.Configuration;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

import de.l3s.gossen.crawler.CrawlUrl;
import de.l3s.gossen.crawler.TimeSpecification;
import de.l3s.gossen.snapshots.ArchiveRecordParser;
import de.l3s.gossen.snapshots.DirectoryPrefixResolver;
import de.l3s.gossen.snapshots.HBaseSnapshotsLocator;
import de.l3s.gossen.snapshots.HdfsSnapshotReader;
import de.l3s.gossen.snapshots.LocationResolver;
import de.l3s.gossen.snapshots.SnaphotLocation;
import de.l3s.gossen.snapshots.Snapshot;
import de.l3s.gossen.snapshots.SnapshotsLocator;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class ArchiveFetcher implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveFetcher.class);
    private final SnapshotsLocator locator;
    private final LocationResolver locationResolver;
    private final HdfsSnapshotReader reader;
    private final ArchiveRecordParser recordParser = new ArchiveRecordParser();
    private final int versionsToCheck;

    public ArchiveFetcher(Configuration conf, String indexPath, String dataPath, MetricRegistry metrics,
            int versionsToCheck) throws IOException {
        this.versionsToCheck = versionsToCheck;
        locator = new HBaseSnapshotsLocator(conf, metrics);
        locationResolver = new DirectoryPrefixResolver(dataPath);
        reader = new HdfsSnapshotReader(conf, metrics);
    }

    public List<Snapshot> get(CrawlUrl url, TimeSpecification referenceTime) throws IOException {
        Iterable<SnaphotLocation> locations = locator.findLocations(url.getUrl());
        locations = referenceTime.findBest(locations, versionsToCheck);
        return stream(locations.spliterator(), false).map(location -> {
            SnaphotLocation resolvedLocation = locationResolver.resolve(location);
            try (ArchiveReader archiveReader = reader.open(resolvedLocation);
                    ArchiveRecord record = archiveReader.get()) {
                return recordParser.readSnapshot(record);
            } catch (Exception e) {
                logger.info("Failed to get snapshot '{}' because of exception ", url, e);
                return null;
            }
        }).filter(Objects::nonNull).collect(toList());
    }

    @Override
    public void close() throws IOException {
        if (locator instanceof Closeable) {
            ((Closeable) locator).close();
        }
    }

}
