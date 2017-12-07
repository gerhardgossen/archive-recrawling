package de.l3s.icrawl.crawler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Test;

import de.l3s.icrawl.crawler.TimeSpecification;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

public class TimeSpecificationTest {

    @Test
    public void testGetDuration() {
        LocalDate end = LocalDate.of(2015, 7, 13);
        ZonedDateTime t = ZonedDateTime.of(2015, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        long diff = Duration.between(end.atStartOfDay(), t).toMillis();
        assertThat(diff, is(lessThan(0L)));
    }

    @Test
    public void testGetRelevance() throws Exception {
        LocalDate start = LocalDate.of(2003, 03, 20);
        LocalDate end = LocalDate.of(2013, 05, 31);
        Period fuzziness = Period.ofDays(180);
        TimeSpecification spec = new TimeSpecification(start, end, fuzziness, fuzziness);
        double relevance = spec.getRelevance(ZonedDateTime.of(2005, 1, 11, 4, 51, 0, 0, ZoneOffset.UTC));
        assertThat(relevance, is(lessThanOrEqualTo(1.0)));
    }

    @Test
    public void testGetRelevanceExp() throws Exception {
        LocalDate start = LocalDate.of(2003, 03, 20);
        LocalDate end = LocalDate.of(2013, 05, 31);
        Period fuzziness = Period.ofDays(180);
        TimeSpecification spec = new TimeSpecification(start, end, fuzziness, fuzziness);
        double relevance = spec.getRelevanceExp(ZonedDateTime.of(2003, 3, 18, 4, 51, 0, 0, ZoneOffset.UTC));
        assertThat(relevance, is(lessThanOrEqualTo(1.0)));
    }

    @Test
    public void testGetRelevance2() throws Exception {
        long seconds = 1105419099000L;
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(seconds), ZoneOffset.UTC);
        LocalDate start = LocalDate.of(2003, 03, 20);
        LocalDate end = LocalDate.of(2013, 05, 31);
        Period fuzziness = Period.ofDays(180);
        TimeSpecification spec = new TimeSpecification(start, end, fuzziness, fuzziness);
        double relevance = spec.getRelevance(date);
        System.out.println(relevance);
        assertThat(relevance, is(lessThanOrEqualTo(1.0)));
    }

}
