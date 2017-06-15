package de.l3s.gossen.contentanalysis;

import java.util.Collections;
import java.util.Iterator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import de.l3s.gossen.contentanalysis.TreeWalker;

import static de.l3s.gossen.crawler.TestUtils.exhaustedIterator;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class TreeWalkerTest {

    @Test
    public void testSimple() {
        String html = "<html><head></head><body><p>Foo</p><p>Bar</p></body></html>";
        Document dom = Jsoup.parse(html);
        Iterator<Node> walker = new TreeWalker(dom, Collections.<String> emptySet()).iterator();
        assertThat(walker.next(), instanceOf(Document.class));
        assertElement(walker.next(), "html");
        assertElement(walker.next(), "head");
        assertElement(walker.next(), "body");
        assertElement(walker.next(), "p");
        assertText(walker.next(), "Foo");
        assertElement(walker.next(), "p");
        assertText(walker.next(), "Bar");
        assertThat(walker, is(exhaustedIterator()));
    }

    @Test
    public void testSkipElements() {
        String html = "<html><head></head><body><p>Foo</p><p>Bar</p></body></html>";
        Document dom = Jsoup.parse(html);
        Iterator<Node> walker = new TreeWalker(dom, ImmutableSet.of("head", "body")).iterator();
        assertThat(walker.next(), instanceOf(Document.class));
        assertElement(walker.next(), "html");
        assertThat(walker, is(exhaustedIterator()));
    }

    @Test
    public void testOtherNodes() {
        String html = "<!doctype html><head><!-- comment --></head><body><p>&quot;</p></body></html>";
        Document dom = Jsoup.parse(html);
        Iterator<Node> walker = new TreeWalker(dom, Collections.<String> emptySet()).iterator();
        assertThat(walker.next(), instanceOf(Document.class));
        assertThat(walker.next(), instanceOf(DocumentType.class));
        assertElement(walker.next(), "html");
        assertElement(walker.next(), "head");
        assertThat(walker.next(), instanceOf(Comment.class));
        assertElement(walker.next(), "body");
        assertElement(walker.next(), "p");
        assertText(walker.next(), "\"");
        assertThat(walker, is(exhaustedIterator()));
    }


    private void assertText(Node node, String content) {
        assertThat(node, instanceOf(TextNode.class));
        assertThat(((TextNode) node).text(), is(content));
    }

    void assertElement(Node node, String tagName) {
        assertThat(node, instanceOf(Element.class));
        assertThat(node.nodeName(), is(tagName));
    }

}
