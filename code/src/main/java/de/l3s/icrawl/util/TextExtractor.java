package de.l3s.icrawl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.XmlDeclaration;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

import static com.google.common.base.CharMatcher.WHITESPACE;
import static java.util.Locale.ROOT;

public class TextExtractor {
    private static final char LINE_BREAK = '\n';
    private static final String FRAGMENT_SEPARATOR = "\n\n";
    private static final Set<String> BLOCK_ELEMENTS = Sets.newHashSet("html", "head", "body",
        "frameset", "script", "noscript", "style", "meta", "link", "title", "frame", "noframes",
        "section", "nav", "aside", "hgroup", "header", "footer", "p", "h1", "h2", "h3", "h4", "h5",
        "h6", "ul", "ol", "pre", "div", "blockquote", "hr", "address", "figure", "figcaption",
        "form", "fieldset", "ins", "del", "s", "dl", "dt", "dd", "li", "table", "caption", "thead",
        "tfoot", "tbody", "colgroup", "col", "tr", "th", "td", "video", "audio", "canvas",
        "details", "menu", "plaintext");
    private static final Set<String> IGNORED_ELEMENTS = Sets.newHashSet("script", "style", "head");

    private TextExtractor() {}

    public static String extractText(Element node) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder sb = new StringBuilder(1024);
        extract(node, paragraphs, sb);
        trimRight(sb);
        if (sb.length() != 0) {
            paragraphs.add(sb.toString());
        }
        return Joiner.on(FRAGMENT_SEPARATOR).join(paragraphs);
    }

    private static void handleTextNode(String text, StringBuilder sb) {
        if (text.isEmpty()) {
            return;
        }
        if (WHITESPACE.matchesAllOf(text) && sb.length() == 0) {
            return;
        }
        if (WHITESPACE.matches(text.charAt(0))) {
            ensureEndsWithSpace(sb);
        }
        sb.append(text.trim());
        if (WHITESPACE.matches(lastChar(text))) {
            sb.append(' ');
        }
    }

    static void ensureEndsWithSpace(StringBuilder sb) {
        if (sb.length() > 0 && !WHITESPACE.matches(lastChar(sb))) {
            sb.append(' ');
        }
    }

    private static char lastChar(CharSequence chars) {
        return chars.charAt(chars.length() - 1);
    }

    static void ensureEndsWithParagraph(StringBuilder sb) {
        if (sb.length() == 0) {
            return;
        } else if (sb.length() == 1) {
            if (!WHITESPACE.matches(sb.charAt(0))) {
                sb.append(FRAGMENT_SEPARATOR);
            }
            return;
        } else if (LINE_BREAK == lastChar(sb)) {
            if (LINE_BREAK == sb.charAt(sb.length() - 2)) {
                return;
            } else {
                sb.append(LINE_BREAK);
            }
        } else {
            trimRight(sb);
            sb.append(FRAGMENT_SEPARATOR);
        }
    }

    static void trimRight(StringBuilder sb) {
        int idx;
        for (idx = sb.length() - 1; idx >= 0 && WHITESPACE.matches(sb.charAt(idx)); idx--) {}
        sb.setLength(idx + 1);
    }

    static boolean isBlockElement(Element elem) {
        return BLOCK_ELEMENTS.contains(elem.tagName().toLowerCase(ROOT));
    }

    static boolean isIgnoredElement(Element elem) {
        return IGNORED_ELEMENTS.contains(elem.tagName().toLowerCase(ROOT));
    }

    public static String extractText(Document doc) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder sb = new StringBuilder(1024);
        for (Element node : doc.children()) {
            extract(node, paragraphs, sb);
        }
        trimRight(sb);
        if (sb.length() != 0) {
            paragraphs.add(sb.toString());
        }
        return Joiner.on(FRAGMENT_SEPARATOR).join(paragraphs);
    }

    private static void extract(Element elem, List<String> paragraphCollector, StringBuilder sb) {
        if (isIgnoredElement(elem)) {
            return;
        } else if (isBlockElement(elem)) {
            trimRight(sb);
            if (sb.length() != 0) {
                paragraphCollector.add(sb.toString());
                sb.setLength(0);
            }
        } else if ("br".equalsIgnoreCase(elem.tagName())) {
            sb.append(LINE_BREAK);
        }
        for (org.jsoup.nodes.Node child : elem.childNodes()) {
            extract(child, paragraphCollector, sb);
        }
    }

    private static void extract(Node node, List<String> paragraphCollector, StringBuilder sb) {
        if (node instanceof Element) {
            extract((Element) node, paragraphCollector, sb);
        } else if (node instanceof TextNode) {
            handleTextNode(((TextNode) node).text(), sb);
        } else if (node instanceof Comment || node instanceof DataNode || node instanceof DocumentType
                || node instanceof XmlDeclaration) {
            // ignore
        } else {
            throw new IllegalArgumentException("Unknown node type " + node.getClass().getName());
        }
    }
}
