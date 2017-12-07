package de.l3s.icrawl.crawler.frontier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Throwables;

import de.l3s.icrawl.crawler.CrawlUrl;

public class FileBasedFrontier extends BaseFrontier implements Frontier {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedFrontier.class);
    final RandomAccessFile[] files;
    private long[] readIndices;
    private long[] writeIndices;
    private final File queueDirectory;
    private final double intervalSize;
    private final WeightedRandomSelector selector;
    private final boolean persist;

    public FileBasedFrontier(File queueDirectory, MetricRegistry metrics, int numQueues, boolean persist)
            throws IOException {
        super(metrics);
        intervalSize = 1.0 / numQueues;
        this.queueDirectory = queueDirectory;
        files = new RandomAccessFile[numQueues];
        int width = Math.max((int) Math.log10(numQueues), 1);
        queueDirectory.mkdirs();
        for (int i = 0; i < numQueues; i++) {
            files[i] = new RandomAccessFile(queueFile(queueDirectory, width, i + 1), "rw");
        }
        File positionsFile = positionsFile(queueDirectory);
        if (persist && positionsFile.exists()) {
            logger.info("Continuing queue from positions in {}", positionsFile.getAbsoluteFile());
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(positionsFile))) {
                for (int i = 0; i < numQueues; i++) {
                    readIndices[i] = is.readLong();
                    writeIndices[i] = is.readLong();
                }
            }
        } else {
            readIndices = new long[numQueues];
            writeIndices = new long[numQueues];
        }
        selector = new WeightedRandomSelector(numQueues, 2);
        this.persist = persist;
    }

    private File queueFile(File queueDirectory, int width, int priority) {
        return new File(queueDirectory, String.format(Locale.ROOT, "%" + width + "f", priority * intervalSize));
    }

    private File positionsFile(File queueDirectory) {
        return new File(queueDirectory, "positions");
    }

    @Override
    protected void pushInternal(CrawlUrl url) {
        try {
            int queueIndex = (int) (url.getPriority() / intervalSize);
            // treat 1.0 equal to .99...
            if (queueIndex == files.length) {
                queueIndex = files.length - 1;
            }
            RandomAccessFile file = files[queueIndex];
            file.seek(writeIndices[queueIndex]);
            file.writeUTF(url.getUrl());
            file.writeUTF(url.getPath());
            file.writeFloat(url.getPriority());
            if (url.getReferrer() != null) {
                file.writeBoolean(true);
                file.writeUTF(url.getReferrer());
                file.writeUTF(url.getRefererCrawlTime().toString());
            } else {
                file.writeBoolean(false);
            }
            writeIndices[queueIndex] = file.getFilePointer();
            selector.enable(queueIndex);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected Optional<CrawlUrl> popInternal() {
        if (allQueuesEmpty()) {
            return Optional.empty();
        }
        try {
            int queueIndex = selector.next();
            if (queueIndex < 0) {
                return Optional.empty();
            }
            RandomAccessFile file = files[queueIndex];
            file.seek(readIndices[queueIndex]);
            String url = file.readUTF();
            String path = file.readUTF();
            float priority = file.readFloat();
            String referrer;
            ZonedDateTime referrerCrawlTime;
            if (file.readBoolean()) {
                referrer = file.readUTF();
                referrerCrawlTime = ZonedDateTime.parse(file.readUTF());
            } else {
                referrer = null;
                referrerCrawlTime = null;
            }
            readIndices[queueIndex] = file.getFilePointer();
            if (isQueueEmpty(queueIndex)) {
                selector.disable(queueIndex);
            }
            return Optional.of(new CrawlUrl(url, path, priority, referrer, referrerCrawlTime));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean allQueuesEmpty() {
        for (int i = 0; i < files.length; i++) {
            if (!isQueueEmpty(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isQueueEmpty(int queueIndex) {
        return readIndices[queueIndex] >= writeIndices[queueIndex];
    }

    @Override
    public void close() throws IOException {
        super.close();
        synchronized (lock) {
            for (RandomAccessFile file : files) {
                file.close();
            }
            if (persist) {
                File positionsFile = positionsFile(queueDirectory);
                try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(positionsFile))) {
                    for (int i = 0; i < files.length; i++) {
                        os.writeLong(readIndices[i]);
                        os.writeLong(writeIndices[i]);
                    }
                }
                logger.info("Wrote current positions to {}", positionsFile.getAbsoluteFile());
            } else {
                for (File file : queueDirectory.listFiles()) {
                    file.delete();
                }
                queueDirectory.delete();
                logger.info("Removed queue in {}", queueDirectory.getAbsoluteFile());
            }
        }
    }

}
