package de.l3s.gossen.util;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Collections;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import static de.l3s.gossen.util.DateUtils.liberalParseDate;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DateUtilsTest {

    @Test
    public void testParseShortTimeZone() {
        ZoneId zoneId = ZoneId.of("Europe/London", ImmutableMap.of("BST", "Europe/London", "CET", "Europe/Berlin"));
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendZoneText(TextStyle.SHORT, Collections.singleton(zoneId))
            .toFormatter();
        TemporalAccessor accessor = formatter.parse("BST");
        ZoneId actual = accessor.query(TemporalQueries.zone());

        assertThat(actual, is(ZoneId.of("Europe/London")));
    }

    @Test
    public void testLiberalParseDate() throws Exception {
        assertThat(liberalParseDate("21.02.2001"), is(ZonedDateTime.of(2001, 2, 21, 0, 0, 0, 0, UTC)));
        assertThat(liberalParseDate("2006-12-31T20:22:10+0100"),
            is(ZonedDateTime.of(2006, 12, 31, 20, 22, 10, 0, ZoneOffset.ofHours(1))));

        assertThat(liberalParseDate("2007-6-8T06:26:14+02:00"),
            is(ZonedDateTime.of(2007, 6, 8, 6, 26, 14, 0, ZoneOffset.ofHours(2))));

        assertThat(liberalParseDate("Sa, 24 Dez 2011 12:46:24 MEZ+0100"),
            is(ZonedDateTime.of(2011, 12, 24, 12, 46, 24, 0, ZoneId.of("Europe/Paris"))));
    }
}
