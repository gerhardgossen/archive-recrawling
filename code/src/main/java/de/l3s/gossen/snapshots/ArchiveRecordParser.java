package de.l3s.gossen.snapshots;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.http.protocol.HTTP;
import org.archive.format.http.HttpHeaders;
import org.archive.format.http.HttpResponse;
import org.archive.format.http.HttpResponseParser;
import org.archive.format.text.charset.CharsetDetector;
import org.archive.format.warc.WARCConstants;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCRecordMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import static de.l3s.gossen.snapshots.Utils.asMap;
import static de.l3s.gossen.snapshots.Utils.parseTimestamp;

public class ArchiveRecordParser {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveRecordParser.class);
    private final HttpResponseParser responseParser;
    private final CharsetDetector charsetDetector;

    public ArchiveRecordParser() {
        this.responseParser = new HttpResponseParser();
        this.charsetDetector = new OnlyHtmlCharsetDetector();
    }

    public Snapshot readSnapshot(ArchiveRecord archiveRecord) throws IOException {
        ArchiveRecordHeader header = archiveRecord.getHeader();
        String originalUrl = header.getUrl();

        HttpResponse response = responseParser.parse(archiveRecord);
        final HttpHeaders headers = response.getHeaders();
        ZonedDateTime timestamp = getCrawlTime(header).orElseGet(() -> serverDate(headers));
        byte[] contentBuffer = ByteStreams.toByteArray(response.getInner());
        Object content;
        String mimetype = headers.getValue(HTTP.CONTENT_TYPE);
        if (mimetype == null) {
            mimetype = header.getMimetype();
        }
        if (mimetype != null && mimetype.startsWith("text")) {
            String charset = charsetDetector.getCharset(contentBuffer, contentBuffer.length, headers);
            content = new String(contentBuffer, charset);
        } else {
            content = contentBuffer;
        }

        int status = response.getMessage().getStatus();
        return new Snapshot(originalUrl, timestamp, status, mimetype, asMap(headers), content);
    }

    private ZonedDateTime serverDate(HttpHeaders headers) {
        String value = null;
        try {
            value = headers.getValueCaseInsensitive(org.apache.http.HttpHeaders.DATE);
            Date date = DateUtil.parseDate(value);
            return ZonedDateTime.from(date.toInstant());
        } catch (DateParseException e) {
            logger.debug("Could not parse HTTP Date header '{}':", value, e);
            return null;
        }
    }

    Optional<ZonedDateTime> getCrawlTime(ArchiveRecordHeader header) {
        String date;
        if (header instanceof ARCRecordMetaData) {
            date = ((ARCRecordMetaData) header).getDate();
            return parseTimestamp(date);
        } else {
            date = (String) header.getHeaderValue(WARCConstants.HEADER_KEY_DATE);
            return Optional.of(ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME));
        }
    }
}
