package de.l3s.icrawl.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.google.common.collect.ImmutableSet;

import static java.util.Locale.ENGLISH;

public final class WebPageUtils {
    private static final Set<String> PARAGRAPH_ELEMENTS = ImmutableSet.of("p", "div", "li", "dd", "dt", "blockquote",
        "pre", "caption", "th", "td");
    private static final int MIN_PARAGRAPH_TOKENS = 50;
    /**
     * Minimal number of tokens a JS string needs to have to be recognized as a
     * string
     */
    private static final int MIN_JS_TOKENS = 3;

    private WebPageUtils() {}

    private static int tokenCount(Element n) {
        return TextExtractor.extractText(n).split("\\s+").length;
    }

    public static Element findParagraphParent(Element startNode, int minParagraphTokens) {
        Element elem = startNode;
        while (elem != null && !isParagraphElement(elem) && needsMoreTokens(elem, minParagraphTokens)
                && elem.parent() instanceof Element) {
            elem = (Element) elem.parentNode();
        }
        return elem;
    }

    public static Element containingElement(Node n) {
        Node currentN = n;
        while (currentN != null && !(n instanceof Element)) {
            currentN = currentN.parentNode();
        }
        return (Element) currentN;
    }

    private static boolean needsMoreTokens(Element elem, int minParagraphTokens) {
        return minParagraphTokens < 0 || tokenCount(elem) < minParagraphTokens;
    }

    private static boolean isParagraphElement(Element elem) {
        return PARAGRAPH_ELEMENTS.contains(elem.tagName().toLowerCase(ENGLISH));
    }

    public static Element findParagraphParent(Element startElement) {
        return findParagraphParent(startElement, MIN_PARAGRAPH_TOKENS);
    }

    public static Element findParagraphParent(Node node, int minParagraphTokens) {
        Node n = Objects.requireNonNull(node);
        while (n != null) {
            if (n instanceof Element) {
                return findParagraphParent((Element) n, minParagraphTokens);
            } else {
                n = n.parentNode();
            }
        }
        return null;
    }

    public static String extractTextFromJavascript(Document dom) {
        StringBuilder sb = new StringBuilder();
        for (Element element : dom.getElementsByTag("script")) {
            String script = element.text();
            extractTextFromJavascript(script, sb);
        }
        return StringEscapeUtils.unescapeJavaScript(sb.toString().trim());
    }

    private static void extractTextFromJavascript(String script, StringBuilder sb) {
        int pos = -1;
        int startOfString = -1;
        while ((pos = script.indexOf("\"", pos + 1)) >= 0) {
            if (startOfString >= 0) {
                String s = script.substring(startOfString + 1, pos);
                if (s.split("\\s+").length > MIN_JS_TOKENS) {
                    sb.append("\n\n").append(s);
                }
                startOfString = -1;
            } else {
                startOfString = pos;
            }
        }
    }

    public static Document parseHtml(InputStream is, String url) {
        try {
            return Jsoup.parse(is, "UTF-8", url);
        } catch (IOException e) {
            throw new HtmlParseException(url, e);
        }
    }

    public static Document parseHtml(String content, String url) {
        return Jsoup.parse(content, url);
    }

    public static boolean hasHtmlContent(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return content.substring(0, Math.min(content.length(), 1024)).toUpperCase(Locale.ROOT).contains("<HTML");
    }
}
