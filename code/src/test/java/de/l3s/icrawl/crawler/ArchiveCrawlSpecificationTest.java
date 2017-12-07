package de.l3s.icrawl.crawler;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

import de.l3s.icrawl.contentanalysis.DocumentVector;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class ArchiveCrawlSpecificationTest {

    @Test
    public void testReadFile() throws IOException {
        ArchiveCrawlSpecification spec = ArchiveCrawlSpecification
            .readFile(new File("src/test/resources/de/l3s/icrawl/crawler/wm2006.json"));
        assertThat(spec.getReferenceDocuments(), contains("https://de.wikipedia.org/wiki/Fu%C3%9Fball-Weltmeisterschaft_2006"));
        assertThat(spec.getSeedUrls(), hasSize(44));
        assertThat(spec.getReferenceTime(), is(notNullValue()));
        assertThat(spec.getReferenceTime().getRelevance(ZonedDateTime.of(2006, 06, 15, 0, 0, 0, 0, ZoneOffset.UTC)), is(1.0));
    }

    @Test
    public void testTimeSpecificationRoundtrip() throws Exception {
        TimeSpecification referenceTime = new TimeSpecification(LocalDate.of(2010, 1, 1), LocalDate.of(2011, 1, 1),
            Period.ofDays(7), Period.ofDays(7));
        ArchiveCrawlSpecification expected = new ArchiveCrawlSpecification("foo", new ArrayList<String>(),
            new ArrayList<String>(), referenceTime, new HashMap<Locale, DocumentVector>(), new HashMap<Locale, Set<String>>(),
            "foo-description", Locale.GERMAN, emptyMap());
        File outFile = File.createTempFile("spec", ".json");
        expected.writeFile(outFile);
        ArchiveCrawlSpecification actual = ArchiveCrawlSpecification.readFile(outFile);
        outFile.delete();
        assertThat(actual.getReferenceTime(), equalTo(referenceTime));
    }
}
