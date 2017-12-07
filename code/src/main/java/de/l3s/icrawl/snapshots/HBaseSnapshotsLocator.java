package de.l3s.icrawl.snapshots;

import java.io.Closeable;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.archive.url.URLKeyMaker;
import org.archive.url.WaybackURLKeyMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;

public class HBaseSnapshotsLocator extends BaseSnapshotsLocator implements SnapshotsLocator, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(HBaseSnapshotsLocator.class);
    private static final TableName TABLE_NAME = TableName.valueOf("CDX2");
    private static final byte[] FAMILY = Bytes.toBytes("c");
    private static final byte[] COL_OFFSET = Bytes.toBytes("offset");
    private static final byte[] COL_CRAWL_TIME = Bytes.toBytes("ts");
    private static final byte[] COL_MIME = Bytes.toBytes("mime");
    private static final byte[] COL_ORIGINAL_URL = Bytes.toBytes("origurl");
    private static final byte[] COL_WARC_FILE = Bytes.toBytes("filename");
    private final Connection connection;

    public HBaseSnapshotsLocator(Configuration conf, MetricRegistry metrics) throws IOException {
        this(conf, new WaybackURLKeyMaker(), metrics);
    }

    public HBaseSnapshotsLocator(Configuration conf, URLKeyMaker keyMaker, MetricRegistry metrics) throws IOException {
        super(keyMaker, metrics);
        connection = ConnectionFactory.createConnection(conf);
    }

    @Override
    protected Optional<SnaphotLocation> findOneInternal(String surt, ZonedDateTime crawlTime) throws IOException {
        Get get = new Get(Bytes.toBytes(surt));
        byte[] tsBytes = Bytes.toBytes(Utils.toString(crawlTime));
        get.setFilter(new SingleColumnValueFilter(FAMILY, COL_CRAWL_TIME, CompareOp.EQUAL, tsBytes));

        try (Table table = connection.getTable(TABLE_NAME)) {
            Result result = table.get(get);
            if (result.isEmpty()) {
                return Optional.empty();
            } else {
                String originalUrl = getString(result, FAMILY, COL_ORIGINAL_URL);
                String warcFile = getString(result, FAMILY, COL_WARC_FILE);
                long warcFileOffset;
                String offset = getString(result, FAMILY, COL_OFFSET);
                try {
                    warcFileOffset = Long.parseLong(offset);
                } catch (NumberFormatException e) {
                    logger.info("Not a valid offset: {}", offset);
                    warcFileOffset = -1;
                }
                long length = -1;
                String mimeType = getString(result, FAMILY, COL_MIME);
                String signature = null;
                SnaphotLocation sl = new SnaphotLocation(originalUrl, crawlTime, warcFile, warcFileOffset, length,
                    mimeType, signature);
                return Optional.of(sl);
            }
        }
    }

    private String getString(Result result, byte[] family, byte[] qualifier) {
        return getString(result.getColumnLatestCell(family, qualifier));
    }

    @Override
    protected List<SnaphotLocation> findInternal(String surt) throws IOException {
        Get get = new Get(Bytes.toBytes(surt));
        get.setMaxVersions();

        try (Table table = connection.getTable(TABLE_NAME)) {
            Result result = table.get(get);

            Map<Long, String> originalUrls = getValuesByVersion(result, FAMILY, COL_ORIGINAL_URL);
            Map<Long, String> crawlTimes = getValuesByVersion(result, FAMILY, COL_CRAWL_TIME);
            Map<Long, String> warcFiles = getValuesByVersion(result, FAMILY, COL_WARC_FILE);
            Map<Long, String> offsets = getValuesByVersion(result, FAMILY, COL_OFFSET);
            Map<Long, String> mimeTypes = getValuesByVersion(result, FAMILY, COL_MIME);

            List<SnaphotLocation> results = Lists.newArrayListWithExpectedSize(originalUrls.size());
            for (Long version : originalUrls.keySet()) {
                Optional<ZonedDateTime> crawlTime = Utils.parseTimestamp(crawlTimes.get(version));
                if (!crawlTime.isPresent()) {
                    logger.info("No valid date for URL '{}'", surt);
                }
                String originalUrl = originalUrls.get(version);
                String warcFile = warcFiles.get(version);
                long warcFileOffset = Long.parseLong(offsets.get(version));
                long length = -1;
                String mimeType = mimeTypes.get(version);
                String signature = null;

                results.add(new SnaphotLocation(originalUrl, crawlTime.orElse(null), warcFile, warcFileOffset, length,
                    mimeType, signature));
            }
            return results;
        }
    }

    private Map<Long, String> getValuesByVersion(Result result, byte[] family, byte[] qualifier) {
        Map<Long, String> values = new HashMap<>();
        for (Cell cell : result.getColumnCells(family, qualifier)) {
            values.put(cell.getTimestamp(), getString(cell));
        }
        return values;
    }

    private String getString(Cell cell) {
        return Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }

}
