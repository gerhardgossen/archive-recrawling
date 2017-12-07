package de.l3s.icrawl.crawler;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;
import io.mola.galimatias.canonicalize.URLCanonicalizer;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

import de.l3s.icrawl.crawler.urls.UrlCanonicalizerNormalizer;
import de.l3s.icrawl.crawler.urls.UrlCanonicalizerNormalizer.StripParametersCanonicalizer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class StripParametersCanonicalizerTest {

    @Test
    public void test() throws GalimatiasParseException {
        URLCanonicalizer canonicalizer = new StripParametersCanonicalizer(Pattern.compile("^urm_"));

        URL url = canonicalizer.canonicalize(URL.parse("http://www.example.org/?b=ä&a= f"));
        assertThat(url.toString(), is("http://www.example.org/?a=%20f&b=%C3%A4"));
    }

    @Test
    public void testRelativeUrl() throws Exception {
        UrlCanonicalizerNormalizer normalizer = new UrlCanonicalizerNormalizer();
        String normalized = normalizer.normalize("http://www.example.org/foo/../bar/");
        assertThat(normalized, is("http://www.example.org/bar/"));
    }

    @Test
    @Ignore
    public void testIndexHtml() throws Exception {
        UrlCanonicalizerNormalizer normalizer = new UrlCanonicalizerNormalizer();
        String normalized = normalizer.normalize("http://www.example.org/index.html");
        assertThat(normalized, is("http://www.example.org/"));
    }

    @Test
    public void testNewline() throws Exception {
        UrlCanonicalizerNormalizer normalizer = new UrlCanonicalizerNormalizer();
        for (String input : Arrays.asList("http://www.example.org/\nfoo", "http://www.example.org/\rfoo",
            "http://www.example.org/\r\nfoo", "http://www.example.org/\tfoo")) {
            String normalized = normalizer.normalize(input);
            assertThat(normalized, is("http://www.example.org/foo"));
        }
    }

}
