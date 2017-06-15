package de.l3s.gossen.crawler.tools;

import org.junit.Test;

import static de.l3s.gossen.crawler.tools.CrawlSpecCreator.cleanUrl;
import static de.l3s.gossen.crawler.tools.CrawlSpecCreator.isAllowedUrl;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class CrawlSpecCreatorTest {

    @Test
    public void testCleanUrls() {
        String url = "http://www.ftd.de/boersen_maerkte/geldanlage/144295.html";
        String url2 = "http://www.npd.de/html/714/artikel/detail/1718/";
        String url3 = "http://www.hnd.bayern.de/pegel/wasserstand/";
        String url4 = "http://idw-online.de/pages/de/news384817";
        String url5 = "http://www.dradio.de/kulturnachrichten/201308101400/7";
        String url6 = "http://www.merkur.de/2010_26_Na__Logo.43166.0.html";
        String url7 = "http://www.btw2002.de/";
        assertThat(cleanUrl("https://web.archive.org/web/20070108012822/" + url), is(url));
        assertThat(cleanUrl("https://archive.is/20130211160428/" + url2), is(url2));
        assertThat(cleanUrl("https://archive.is/" + url3 + "*"), is(url3));
        assertThat(cleanUrl("http://www.webcitation.org/5sdqGDLvi?url=" + url4), is(url4));
        assertThat(cleanUrl("http://derefer.unbubble.eu?u=" + url5), is(url5));
        assertThat(cleanUrl("http://deadurl.invalid/" + url6), is(url6));

        assertTrue(isAllowedUrl(url7));
    }

}
