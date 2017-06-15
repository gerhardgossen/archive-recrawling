package de.l3s.gossen.crawler;

import java.io.IOException;

import org.junit.Test;

import com.google.common.io.Resources;

import de.l3s.gossen.crawler.urls.RegexUrlNormalizer;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class RegexUrlNormalizerTest {

    @Test
    public void testNormalize() throws IOException {
        RegexUrlNormalizer normalizer = new RegexUrlNormalizer(Resources.getResource("default-regex-normalizers.xml"));
        assertThat(normalizer.normalize("http://www.example.org/?&foo=bar#baz"), is("http://www.example.org/?foo=bar"));
    }

}
