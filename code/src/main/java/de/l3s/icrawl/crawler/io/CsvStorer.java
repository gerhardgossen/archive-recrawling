package de.l3s.icrawl.crawler.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.l3s.icrawl.crawler.CrawlUrl;
import de.l3s.icrawl.crawler.CrawledResource;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CsvStorer implements ResultStorer, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(CsvStorer.class);

    private static class WriterThread implements Runnable, Closeable {

        private boolean stopped = false;
        private final Writer writer;
        private final TransferQueue<String> queue;
        private final Path outputFile;

        public WriterThread(Configuration conf, Path outputFile, TransferQueue<String> queue) throws IOException {
            this.outputFile = outputFile;
            this.queue = queue;
            writer = new OutputStreamWriter(FileSystem.get(conf).create(outputFile, true, 8048), UTF_8);
        }

        @Override
        public void run() {
            try {
                while (!stopped) {
                    String message = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        writer.write(message);
                    }
                }
            } catch (InterruptedException e) {
                logger.info("Exception while reading from queue, aborting", e);
            } catch (IOException e) {
                logger.info("Could not write to file {}, aborting", outputFile, e);
            } finally {
                close();
            }
        }

        @Override
        public void close() {
            logger.info("Closing storer for {}", outputFile);
            try {
                writer.close();
            } catch (IOException e) {
                logger.info("Exception while closing writer:", e);
            }
        }

        public void stop() {
            int waiting = queue.getWaitingConsumerCount();
            Collection<String> buffer = new ArrayList<>(waiting);
            while (queue.drainTo(buffer) > 0) {
                try {
                    for (String message : buffer) {
                        writer.write(message);
                    }
                } catch (IOException e) {
                    logger.info("Exception while draining remaining messages", e);
                }
            }
            this.stopped = true;
        }

    }

    private final TransferQueue<String> queue;
    private final WriterThread writer;

    public CsvStorer(Configuration conf, Path outputFile) throws IOException {
        this.queue = new LinkedTransferQueue<>();
        this.writer = new WriterThread(conf, outputFile, queue);
        Executors.newSingleThreadExecutor().submit(writer);
        sendMessage(
            "url\tcrawlTime\tpath\tstatus\tcrawlPriority\trelevance\tmodifiedDate\tsnapshotsDuration\tminRelevance\tmaxRelevance%n");
    }

    @Override
    public void store(CrawledResource resource) {
        sendMessage("%s\t%s\t%s\t%d\t%f\t%f\t%s\t%s\t%f\t%f%n", resource.getUrl(),
            resource.getResource().getCrawlTime(), resource.getPath(), resource.getResource().getStatus(),
            resource.getCrawlPriority(), resource.getRelevance(), resource.getModifiedDate(),
            resource.getSnapshotsDuration(), resource.getMinRelevance(), resource.getMaxRelevance());
    }

    @Override
    public void storeNotFound(CrawlUrl url) {
        sendMessage("%s\t-\t%s\t404\t%f\t-%n", url.getUrl(), url.getPath(), url.getPriority());
    }

    private void sendMessage(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        try {
            boolean sent = queue.tryTransfer(message, 30, TimeUnit.SECONDS);
            if (!sent) {
                logger.debug("Could not send message '{}' (timeout), dropping it", message);
            }
        } catch (InterruptedException e) {
            logger.info("Interrupted while sending message '{}', dropping it", message, e);
        }
    }

    @Override
    public void close() throws IOException {
        writer.stop();
    }

}
