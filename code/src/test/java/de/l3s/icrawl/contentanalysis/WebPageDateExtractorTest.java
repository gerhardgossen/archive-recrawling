package de.l3s.icrawl.contentanalysis;

import java.time.ZonedDateTime;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.junit.Test;

import de.l3s.icrawl.contentanalysis.WebPageDateExtractor;
import de.l3s.icrawl.contentanalysis.WebPageDateExtractor.WebPageDate;
import de.l3s.icrawl.crawler.TestUtils;

import static de.l3s.icrawl.contentanalysis.WebPageDateExtractor.findDateMatch;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class WebPageDateExtractorTest {

    @Test
    public void testFindDateMatch() {
        assertThat(findDateMatch("28.9.2009 0:00 Uhr"), is(ZonedDateTime.of(2009, 9, 28, 0, 0, 0, 0, UTC)));
        assertThat(findDateMatch("09. Jun 2010, 15:01 Uhr"), is(ZonedDateTime.of(2010, 6, 9, 15, 1, 0, 0, UTC)));
        assertThat(findDateMatch("Do., 16.09.10"), is(ZonedDateTime.of(2010, 9, 16, 0, 0, 0, 0, UTC)));
        assertThat(findDateMatch("17. September 2010, 17:43 Uhr"),
            is(ZonedDateTime.of(2010, 9, 17, 17, 43, 0, 0, UTC)));
    }

    @Test
    public void testFindDomRoot() throws Exception {
        String url = "http://sonne.cpfs.mpg.de:4000/index.php?title=Special:RecentChanges&hideanons=1&limit=100&from=20110202154021";
        Document doc = TestUtils.loadDocument(getClass(), "walker-test.html", url);
        Node domRoot = WebPageDateExtractor.findDomRoot(doc);
        assertThat(domRoot, is(notNullValue()));
        WebPageDate date = WebPageDateExtractor.getModifiedDate(url, doc, null, null);
        System.out.println(date);
    }
}
