package de.l3s.icrawl.util;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import de.l3s.icrawl.crawler.TestUtils;
import de.l3s.icrawl.util.TextExtractor;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TextExtractorTest {

    @Test
    public void testExtractDocumentFragment() throws Exception {
        Document document = TestUtils.loadDocument(getClass(), "text.html", "http://example.colm/");
        assertThat(TextExtractor.extractText(document), equalTo("Foo\n\nBar baz"));
    }

    @Test
    public void testIsBlockElement() {
        Element elem = elementWithTagName("P");
        assertTrue(TextExtractor.isBlockElement(elem));
    }

    @Test
    public void testIsNotBlockElement() throws Exception {
        assertFalse(TextExtractor.isBlockElement(elementWithTagName("a")));
    }

    @Test
    public void testIsNotBlockElementMadeUp() throws Exception {
        assertFalse(TextExtractor.isBlockElement(elementWithTagName("foo")));
    }

    Element elementWithTagName(String name) {
        Element elem = mock(Element.class);
        when(elem.tagName()).thenReturn(name);
        return elem;
    }

    @Test
    public void testIsIgnoredElement() {
        assertTrue(TextExtractor.isIgnoredElement(elementWithTagName("STYLE")));
        assertTrue(TextExtractor.isIgnoredElement(elementWithTagName("script")));
    }
}
