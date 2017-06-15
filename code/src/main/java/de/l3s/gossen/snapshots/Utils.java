package de.l3s.gossen.snapshots;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.archive.format.http.HttpHeader;
import org.archive.format.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

public final class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    private static final DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendValue(ChronoField.YEAR, 4)
        .appendPattern("MMddHHmm[ss]")
        .toFormatter();

    private Utils() {
        // forbid instantiation
    }

    public static Optional<ZonedDateTime> parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(timestamp, dateFormatter).atZone(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            logger.debug("Invalid date header '{}'", timestamp, e);
            return Optional.empty();
        }
    }

    public static String toString(ZonedDateTime crawlTime) {
        return crawlTime.format(dateFormatter);
    }

    static Map<String, String> asMap(HttpHeaders headers) {
        Map<String, String> ret = new HashMap<>(headers.size(), 1.0f);
        for (HttpHeader header : headers) {
            ret.put(header.getName(), header.getValue());
        }
        return ret;
    }

    static Properties readProperties(String fileName) throws IOException {
        Properties props = new Properties();
        try (InputStream is = findResource(fileName)) {
            props.load(is);
        }
        return props;
    }

    private static InputStream findResource(String fileName) throws FileNotFoundException, IOException {
        File propertiesFile = new File(fileName);
        if (propertiesFile.exists()) {
            return new FileInputStream(propertiesFile);
        } else {
            try {
                return Resources.getResource(fileName).openStream();
            } catch (IllegalArgumentException e) {
                throw new FileNotFoundException(fileName + " not found in either current directory or classpath");
            }
        }
    }
}
