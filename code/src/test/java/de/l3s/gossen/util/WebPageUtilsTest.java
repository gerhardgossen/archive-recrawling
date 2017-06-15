package de.l3s.gossen.util;

import java.io.InputStream;

import org.jsoup.nodes.Document;
import org.junit.Test;

import com.google.common.io.Resources;

import de.l3s.gossen.util.WebPageUtils;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class WebPageUtilsTest {

    @Test
    public void testParseHtml() {
        Document document = WebPageUtils.parseHtml("<html><head><title>Foo</title></head><body>Foo!</body></html>",
            "http://www.example.org/");
        assertThat(document.getElementsByTag("title"), hasSize(1));
    }

    @Test
    public void testParseHtmlIS() throws Exception {
        String url = "http://www.epd.de/zentralredaktion/epd-zentralredaktion/schneider-begr%C3%BC%C3%9Ft-beschluss-zur-kirchenfusion-im-norden";
        try (InputStream is = Resources.getResource("parse.html").openStream()) {
            Document doc = WebPageUtils.parseHtml(is, url);
            assertThat(doc.getElementsByTag("title"), hasSize(1));
        }
    }
}
