package de.l3s.icrawl.snapshots;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import static com.codahale.metrics.MetricRegistry.name;

public class HdfsSnapshotReader implements SnapshotReader {
    private final FileSystem fs;
    private final Meter recordRate;
    private final Timer timer;

    public HdfsSnapshotReader(Configuration conf, MetricRegistry metrics) throws IOException {
        fs = FileSystem.get(conf);
        recordRate = metrics.meter(name(getClass(), "records"));
        timer = metrics.timer(name(getClass(), "ioTime"));
    }

    @Override
    public ArchiveReader open(SnaphotLocation location) throws IOException {
        try (Timer.Context context = timer.time()) {
            recordRate.mark();
            FSDataInputStream is = fs.open(new Path(location.getWarcFile()));
            is.seek(location.getWarcFileOffset());
            return ArchiveReaderFactory.get(location.getWarcFile(), is, false);
        }
    }
}
