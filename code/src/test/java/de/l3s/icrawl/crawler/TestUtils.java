package de.l3s.icrawl.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.common.io.Resources;

public final class TestUtils {
    private TestUtils() {
        throw new UnsupportedOperationException();
    }

    public static Matcher<Optional<?>> present() {
        return new TypeSafeMatcher<Optional<?>>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("present value");
            }

            @Override
            protected boolean matchesSafely(Optional<?> item) {
                return item.isPresent();
            }
        };
    }

    public static Document loadDocument(Class<?> contextClass, String resourceName, String origUrl) throws IOException {
        URL url = Resources.getResource(contextClass, resourceName);
        try (InputStream is = url.openStream()) {
            return Jsoup.parse(is, "UTF-8", origUrl);
        }
    }

    public static <E> Matcher<Iterator<? extends E>> exhaustedIterator() {
        return new TypeSafeMatcher<Iterator<? extends E>>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("an exhausted iterator");
            }

            @Override
            protected boolean matchesSafely(Iterator<? extends E> iter) {
                return !iter.hasNext();
            }
        };
    }
}
